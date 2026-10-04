// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.AccessRequest;
import org.devlive.grantforge.authz.domain.AccessRequestRepository;
import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.devlive.grantforge.authz.domain.RequestableRole;
import org.devlive.grantforge.authz.domain.RequestableRoleRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Access requests (D-74): an account asks for a requestable role for some days, with a reason; an approver grants it,
 * which assigns the role until the period is over, or turns it down. Approving goes through the role assignment rules:
 * the approver must have what the role allows, separation of duties applies, and nobody approves their own request.
 * Grants end on time by their assignment's validity; ended grants are taken back by {@link #expire}. Every method but
 * {@link #expire} must be called with the tenant bound.
 */
@Service
public class AccessRequestService
{
    /** The most requests a list shows. */
    static final int LIST_LIMIT = 200;

    private final AccessRequestRepository requests;
    private final RequestableRoleRepository requestable;
    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final RoleAssignmentService assigner;
    private final EffectiveRoles effectiveRoles;
    private final SubjectDirectory subjects;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param requests the requests
     * @param requestable the roles that may be asked for
     * @param roles the roles
     * @param assignments the assignments, to take grants back
     * @param assigner assigns approved roles under the assignment rules
     * @param effectiveRoles tells whether an account holds a role already
     * @param subjects names accounts
     * @param audit records every step
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public AccessRequestService(AccessRequestRepository requests, RequestableRoleRepository requestable, RoleRepository roles,
            RoleAssignmentRepository assignments, RoleAssignmentService assigner, EffectiveRoles effectiveRoles, SubjectDirectory subjects,
            AuditLog audit, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.requests = requireNonNull(requests, "requests");
        this.requestable = requireNonNull(requestable, "requestable");
        this.roles = requireNonNull(roles, "roles");
        this.assignments = requireNonNull(assignments, "assignments");
        this.assigner = requireNonNull(assigner, "assigner");
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.subjects = requireNonNull(subjects, "subjects");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists the roles that may be asked for.
     *
     * @return the roles by name
     */
    public List<RequestableRoleView> requestableRoles()
    {
        return requireNonNull(transactions.execute(status -> {
            List<RequestableRole> settings = requestable.findAll();
            Map<Long, Role> named = byId(settings.stream().map(RequestableRole::getRoleId).toList());
            List<RequestableRoleView> views = new ArrayList<>();
            for (RequestableRole setting : settings) {
                Role role = named.get(setting.getRoleId());
                if (role != null) {
                    views.add(new RequestableRoleView(RoleView.from(role), setting.getMaxDays()));
                }
            }
            views.sort(Comparator.comparing(view -> view.role().name()));
            return views;
        }));
    }

    /**
     * Replaces the roles that may be asked for. System roles, such as the tenant's administrators, cannot be.
     *
     * @param actorId the administrator
     * @param maxDays the longest period of each requestable role, by role
     * @return the roles that may be asked for now
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown role or
     *         {@link AuthzErrorCode#ACCESS_REQUEST_INVALID} for a system role or a period out of range
     */
    public List<RequestableRoleView> setRequestable(long actorId, Map<Long, Integer> maxDays)
    {
        transactions.executeWithoutResult(status -> {
            Map<Long, Role> named = byId(maxDays.keySet());
            for (Map.Entry<Long, Integer> entry : maxDays.entrySet()) {
                Role role = named.get(entry.getKey());
                if (role == null) {
                    throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + entry.getKey());
                }
                if (role.getType() == RoleType.SYSTEM) {
                    throw invalid("system roles cannot be asked for");
                }
                int days = entry.getValue();
                if (days < 1 || days > RequestableRole.MAX_DAYS) {
                    throw invalid("the longest period must be 1-" + RequestableRole.MAX_DAYS + " days");
                }
            }
            requestable.deleteAllInBatch(requestable.findAll());
            requestable.flush();
            requestable.saveAll(maxDays.entrySet().stream().map(entry -> RequestableRole.of(entry.getKey(), entry.getValue())).toList());
            audit.recordWithChange(new AuditRecord(AuditAction.REQUESTABLE_ROLES_CHANGED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(),
                    actorId, null, null, named.values().stream().map(Role::getCode).sorted().collect(Collectors.joining(","))));
        });
        return requestableRoles();
    }

    /**
     * Lists the roles an account may ask for, with whether it holds or asked for each.
     *
     * @param accountId the account
     * @return the options by role name
     */
    public List<RequestOption> optionsFor(long accountId)
    {
        return requireNonNull(transactions.execute(status -> {
            Set<Long> held = held(accountId);
            Set<Long> pending = requests.findByRequesterIdOrderByCreatedAtDescIdDesc(accountId, Limit.of(LIST_LIMIT)).stream()
                    .filter(request -> request.getStatus() == AccessRequestStatus.PENDING).map(AccessRequest::getRoleId).collect(Collectors.toSet());
            return requestableRoles().stream().filter(view -> view.role().enabled())
                    .map(view -> new RequestOption(view.role(), view.maxDays(), held.contains(view.role().id()), pending.contains(view.role().id())))
                    .toList();
        }));
    }

    /**
     * Asks for a role.
     *
     * @param accountId the account asking
     * @param roleId the role
     * @param reason why, 1 to 500 characters
     * @param days for how many days, up to the role's longest period
     * @return the request
     * @throws GrantForgeException with {@link AuthzErrorCode#ACCESS_NOT_REQUESTABLE}, {@link AuthzErrorCode#ACCESS_ALREADY_HELD},
     *         {@link AuthzErrorCode#ACCESS_REQUEST_PENDING} or {@link AuthzErrorCode#ACCESS_REQUEST_INVALID}
     */
    public AccessRequestView request(long accountId, long roleId, @Nullable String reason, int days)
    {
        String why = Strings.blankToNull(reason);
        if (why == null || why.length() > AccessRequest.TEXT_MAX) {
            throw invalid("a reason of at most " + AccessRequest.TEXT_MAX + " characters is required");
        }
        AccessRequestView filed = requireNonNull(transactions.execute(status -> {
            RequestableRole setting = requestable.findByRoleId(roleId).orElseThrow(() -> new GrantForgeException(AuthzErrorCode.ACCESS_NOT_REQUESTABLE,
                    "role " + roleId + " cannot be asked for"));
            Role role = roles.findById(roleId).filter(Role::isEnabled).orElseThrow(() -> new GrantForgeException(AuthzErrorCode.ACCESS_NOT_REQUESTABLE,
                    "role " + roleId + " is disabled"));
            if (days < 1 || days > setting.getMaxDays()) {
                throw invalid("ask for 1-" + setting.getMaxDays() + " days");
            }
            if (held(accountId).contains(roleId)) {
                throw new GrantForgeException(AuthzErrorCode.ACCESS_ALREADY_HELD, "account " + accountId + " holds role " + roleId, role.getName());
            }
            if (requests.existsByRequesterIdAndRoleIdAndStatus(accountId, roleId, AccessRequestStatus.PENDING)) {
                throw new GrantForgeException(AuthzErrorCode.ACCESS_REQUEST_PENDING, "account " + accountId + " asked for role " + roleId,
                        role.getName());
            }
            AccessRequest request = requests.saveAndFlush(AccessRequest.file(accountId, roleId, why, days));
            record(AuditAction.ACCESS_REQUESTED, accountId, request, why);
            return view(request);
        }));
        return filed;
    }

    /**
     * Lists an account's requests, newest first.
     *
     * @param accountId the account
     * @return the requests
     */
    public List<AccessRequestView> mine(long accountId)
    {
        return requireNonNull(transactions.execute(status ->
                views(requests.findByRequesterIdOrderByCreatedAtDescIdDesc(accountId, Limit.of(LIST_LIMIT)))));
    }

    /**
     * Lists requests in some states, newest first, for approvers.
     *
     * @param statuses the states; all if empty
     * @return the requests
     */
    public List<AccessRequestView> list(Collection<AccessRequestStatus> statuses)
    {
        Set<AccessRequestStatus> wanted = statuses.isEmpty() ? Set.of(AccessRequestStatus.values()) : Set.copyOf(statuses);
        return requireNonNull(transactions.execute(status ->
                views(requests.findByStatusInOrderByCreatedAtDescIdDesc(wanted, Limit.of(LIST_LIMIT)))));
    }

    /**
     * Withdraws one's own pending request.
     *
     * @param accountId the account that asked
     * @param id the request
     * @return the request
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#ACCESS_REQUEST_DECIDED}
     */
    public AccessRequestView cancel(long accountId, long id)
    {
        return requireNonNull(transactions.execute(status -> {
            AccessRequest request = require(id);
            if (request.getRequesterId() != accountId) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no request " + id + " of account " + accountId);
            }
            requirePending(request);
            request.cancel(clock.instant());
            record(AuditAction.ACCESS_REQUEST_CANCELLED, accountId, request, null);
            return view(request);
        }));
    }

    /**
     * Grants a pending request: the requester gets the role until the period is over.
     *
     * @param approverId the approver, who must not be the requester and must have what the role allows
     * @param id the request
     * @param days for how many days, at most what was asked for; {@code null} for that
     * @param comment what the approver says, or {@code null}
     * @return the request
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link AuthzErrorCode#ACCESS_REQUEST_DECIDED},
     *         {@link AuthzErrorCode#ACCESS_SELF_APPROVAL}, {@link AuthzErrorCode#ACCESS_REQUEST_INVALID}, or a refusal of the
     *         assignment such as {@link AuthzErrorCode#ROLE_EXCEEDS_ACTOR} or {@link AuthzErrorCode#SOD_CONFLICT}
     */
    public AccessRequestView approve(long approverId, long id, @Nullable Integer days, @Nullable String comment)
    {
        String said = comment(comment);
        return requireNonNull(transactions.execute(status -> {
            AccessRequest request = decidable(approverId, id);
            int granted = days == null ? request.getRequestedDays() : days;
            if (granted < 1 || granted > request.getRequestedDays()) {
                throw invalid("grant 1-" + request.getRequestedDays() + " days");
            }
            Instant now = clock.instant();
            Instant until = now.plus(Duration.ofDays(granted));
            AssignmentView assignment = assigner.assign(approverId, request.getRoleId(), SubjectType.USER, request.getRequesterId(),
                    new RoleAssignment.Terms(null, until, false));
            request.approve(approverId, now, said, assignment.id(), until);
            record(AuditAction.ACCESS_REQUEST_APPROVED, approverId, request, said);
            return view(request);
        }));
    }

    /**
     * Turns a pending request down.
     *
     * @param approverId the approver, who must not be the requester
     * @param id the request
     * @param comment why, or {@code null}
     * @return the request
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link AuthzErrorCode#ACCESS_REQUEST_DECIDED} or
     *         {@link AuthzErrorCode#ACCESS_SELF_APPROVAL}
     */
    public AccessRequestView reject(long approverId, long id, @Nullable String comment)
    {
        String said = comment(comment);
        return requireNonNull(transactions.execute(status -> {
            AccessRequest request = decidable(approverId, id);
            request.reject(approverId, clock.instant(), said);
            record(AuditAction.ACCESS_REQUEST_REJECTED, approverId, request, said);
            return view(request);
        }));
    }

    /**
     * Ends an approved grant before its period is over and takes the role back.
     *
     * @param approverId the approver
     * @param id the request
     * @return the request
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#ACCESS_REQUEST_DECIDED}
     */
    public AccessRequestView revoke(long approverId, long id)
    {
        return requireNonNull(transactions.execute(status -> {
            AccessRequest request = require(id);
            if (request.getStatus() != AccessRequestStatus.APPROVED) {
                throw decided(request);
            }
            takeBack(request);
            request.end(clock.instant(), true);
            record(AuditAction.ACCESS_GRANT_REVOKED, approverId, request, null);
            return view(request);
        }));
    }

    /**
     * Takes back the roles of every tenant's grants whose period is over. The assignments stopped granting anything when
     * the period ended; this removes them and closes the requests.
     *
     * @return how many grants were taken back
     */
    public int expire()
    {
        Instant now = clock.instant();
        List<AccessRequest> ended = TenantContext.callAsSystem(() -> requests.findByStatusAndValidUntilLessThanEqual(AccessRequestStatus.APPROVED, now));
        for (AccessRequest found : ended) {
            long tenantId = requireNonNull(found.getTenantId(), "tenantId");
            long id = found.requireId();
            TenantContext.runInTenant(tenantId, () -> transactions.executeWithoutResult(status -> {
                AccessRequest request = require(id);
                if (request.getStatus() == AccessRequestStatus.APPROVED) {
                    takeBack(request);
                    request.end(now, false);
                    audit.recordWithChange(new AuditRecord(AuditAction.ACCESS_GRANT_EXPIRED, AuditOutcome.SUCCESS, tenantId, null, null,
                            Long.toString(request.getRequesterId()), Long.toString(request.getRoleId())));
                }
            }));
        }
        return ended.size();
    }

    private void takeBack(AccessRequest request)
    {
        Long assignmentId = request.getAssignmentId();
        if (assignmentId != null) {
            assignments.findById(assignmentId).ifPresent(assignments::delete);
            assignments.flush();
        }
    }

    private AccessRequest decidable(long approverId, long id)
    {
        AccessRequest request = require(id);
        requirePending(request);
        if (request.getRequesterId() == approverId) {
            throw new GrantForgeException(AuthzErrorCode.ACCESS_SELF_APPROVAL, "account " + approverId + " cannot decide its own request");
        }
        return request;
    }

    private Set<Long> held(long accountId)
    {
        return effectiveRoles.of(accountId, clock.instant()).stream().filter(EffectiveRole::active).map(role -> role.role().id())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static void requirePending(AccessRequest request)
    {
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw decided(request);
        }
    }

    private AccessRequest require(long id)
    {
        return requests.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no request " + id));
    }

    private Map<Long, Role> byId(Collection<Long> ids)
    {
        return roles.findAllById(ids).stream().collect(Collectors.toMap(Role::requireId, Function.identity()));
    }

    private List<AccessRequestView> views(List<AccessRequest> found)
    {
        return found.stream().map(this::viewOf).flatMap(Optional::stream).toList();
    }

    private AccessRequestView view(AccessRequest request)
    {
        return viewOf(request).orElseThrow(() -> new IllegalStateException("request " + request.getId() + " lost its account or role"));
    }

    private Optional<AccessRequestView> viewOf(AccessRequest request)
    {
        Set<Long> people = new HashSet<>(List.of(request.getRequesterId()));
        Long decider = request.getDecidedBy();
        if (decider != null) {
            people.add(decider);
        }
        Map<Long, Subject> names = subjects.names(SubjectType.USER, people);
        Subject requester = names.get(request.getRequesterId());
        Role role = roles.findById(request.getRoleId()).orElse(null);
        if (requester == null || role == null) {
            return Optional.empty();
        }
        return Optional.of(new AccessRequestView(request.requireId(), requester, RoleView.from(role), request.getReason(),
                request.getRequestedDays(), request.getStatus(), requireNonNull(request.getCreatedAt(), "createdAt"),
                decider == null ? null : names.get(decider), request.getDecidedAt(), request.getDecisionComment(), request.getValidUntil(),
                request.getEndedAt()));
    }

    private void record(AuditAction action, long actorId, AccessRequest request, @Nullable String reason)
    {
        audit.recordWithChange(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(request.requireId()), reason));
    }

    private static @Nullable String comment(@Nullable String comment)
    {
        String said = Strings.blankToNull(comment);
        if (said != null && said.length() > AccessRequest.TEXT_MAX) {
            throw invalid("a comment is at most " + AccessRequest.TEXT_MAX + " characters");
        }
        return said;
    }

    private static GrantForgeException decided(AccessRequest request)
    {
        return new GrantForgeException(AuthzErrorCode.ACCESS_REQUEST_DECIDED, "request " + request.getId() + " is " + request.getStatus(),
                request.getStatus().name());
    }

    private static GrantForgeException invalid(String reason)
    {
        return new GrantForgeException(AuthzErrorCode.ACCESS_REQUEST_INVALID, "invalid access request: " + reason, reason);
    }
}
