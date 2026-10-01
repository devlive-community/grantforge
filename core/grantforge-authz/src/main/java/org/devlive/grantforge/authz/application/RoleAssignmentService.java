// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Who has which role in the bound tenant: assignments to accounts, groups, departments and positions, and the
 * roles an account ends up with. Until roles grant role administration themselves, tenant administrators manage
 * assignments; only platform administrators give the platform administrator role. System accounts keep their
 * system roles. Every method must be called with the actor's tenant bound.
 */
@Service
public final class RoleAssignmentService
{
    private final RoleAssignmentRepository assignments;
    private final RoleRepository roles;
    private final UserAccountRepository accounts;
    private final SubjectDirectory subjects;
    private final CatalogAccess access;
    private final PlatformAdministrators platform;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param assignments assignments of the bound tenant
     * @param roles roles of the bound tenant
     * @param accounts accounts, to protect system accounts
     * @param subjects names subjects and finds an account's memberships
     * @param access tells tenant administrators apart
     * @param platform tells platform administrators apart
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param clock the current time, for validity
     */
    public RoleAssignmentService(RoleAssignmentRepository assignments, RoleRepository roles, UserAccountRepository accounts,
            SubjectDirectory subjects, CatalogAccess access, PlatformAdministrators platform, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.assignments = requireNonNull(assignments, "assignments");
        this.roles = requireNonNull(roles, "roles");
        this.accounts = requireNonNull(accounts, "accounts");
        this.subjects = requireNonNull(subjects, "subjects");
        this.access = requireNonNull(access, "access");
        this.platform = requireNonNull(platform, "platform");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists who has a role, by assignment date.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @return the assignments; subjects deleted in the meantime are left out
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public List<AssignmentView> list(long actorId, long roleId)
    {
        access.requireTenantAdministrator(actorId);
        return requireNonNull(transactions.execute(status -> {
            requireRole(roleId);
            return views(assignments.findByRole(roleId), clock.instant());
        }));
    }

    /**
     * Gives a role to a subject.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param type what the subject is
     * @param subjectId the subject
     * @param terms validity and reach
     * @return the assignment
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown role or subject, {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE}, {@link AuthzErrorCode#ASSIGNMENT_EXISTS}
     *         or {@link AuthzErrorCode#ASSIGNMENT_PERIOD_INVALID}
     */
    public AssignmentView assign(long actorId, long roleId, SubjectType type, long subjectId, RoleAssignment.Terms terms)
    {
        requireNonNull(type, "type");
        AssignmentView view = write(actorId, () -> {
            Role role = requireRole(roleId);
            requireAssignable(actorId, role);
            Subject subject = subjects.find(type, subjectId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no " + type + " " + subjectId));
            if (assignments.findByRoleIdAndSubjectTypeAndSubjectId(roleId, type, subjectId).isPresent()) {
                throw new GrantForgeException(AuthzErrorCode.ASSIGNMENT_EXISTS, type + " " + subjectId + " has role " + roleId);
            }
            RoleAssignment assignment = assignments.saveAndFlush(period(() -> RoleAssignment.create(roleId, type, subjectId, terms)));
            return view(assignment, subject, clock.instant());
        });
        record(AuditAction.ROLE_ASSIGNED, actorId, view);
        return view;
    }

    /**
     * Changes how long and how widely an assignment applies.
     *
     * @param actorId the account asking
     * @param id the assignment
     * @param terms the new validity and reach
     * @return the assignment
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE}, {@link AuthzErrorCode#ASSIGNMENT_PROTECTED} or
     *         {@link AuthzErrorCode#ASSIGNMENT_PERIOD_INVALID}
     */
    public AssignmentView change(long actorId, long id, RoleAssignment.Terms terms)
    {
        AssignmentView view = write(actorId, () -> {
            RoleAssignment assignment = requireAssignment(id);
            Role role = requireRole(assignment.getRoleId());
            requireAssignable(actorId, role);
            requireUnprotected(role, assignment);
            period(() -> {
                assignment.change(terms);
                return assignment;
            });
            assignments.saveAndFlush(assignment);
            return views(List.of(assignment), clock.instant()).get(0);
        });
        record(AuditAction.ROLE_ASSIGNMENT_CHANGED, actorId, view);
        return view;
    }

    /**
     * Takes a role from a subject.
     *
     * @param actorId the account asking
     * @param id the assignment
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE} or {@link AuthzErrorCode#ASSIGNMENT_PROTECTED}
     */
    public void remove(long actorId, long id)
    {
        AssignmentView view = write(actorId, () -> {
            RoleAssignment assignment = requireAssignment(id);
            Role role = requireRole(assignment.getRoleId());
            requireAssignable(actorId, role);
            requireUnprotected(role, assignment);
            AssignmentView removed = views(List.of(assignment), clock.instant()).stream().findFirst()
                    .orElseGet(() -> view(assignment, new Subject(assignment.getSubjectType(), assignment.getSubjectId(),
                            Long.toString(assignment.getSubjectId()), null), clock.instant()));
            assignments.delete(assignment);
            return removed;
        });
        record(AuditAction.ROLE_UNASSIGNED, actorId, view);
    }

    /**
     * Returns the roles an account has, with every assignment that gives each: direct ones and those through its
     * groups, departments (and parent departments whose assignments include sub-departments) and positions.
     *
     * @param actorId the account asking
     * @param accountId the account
     * @return the roles, active ones first, then by name
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public List<EffectiveRole> rolesOf(long actorId, long accountId)
    {
        access.requireTenantAdministrator(actorId);
        return requireNonNull(transactions.execute(status -> {
            if (!accounts.existsById(accountId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no account " + accountId);
            }
            return effective(accountId, clock.instant());
        }));
    }

    private List<EffectiveRole> effective(long accountId, Instant now)
    {
        SubjectDirectory.Memberships memberships = subjects.memberships(accountId);
        List<RoleAssignment> found = new ArrayList<>(assignments.findBySubjects(SubjectType.USER, List.of(accountId)));
        if (!memberships.groups().isEmpty()) {
            found.addAll(assignments.findBySubjects(SubjectType.GROUP, memberships.groups()));
        }
        if (!memberships.units().isEmpty()) {
            found.addAll(assignments.findBySubjects(SubjectType.ORG_UNIT, memberships.units()));
        }
        if (!memberships.parentUnits().isEmpty()) {
            assignments.findBySubjects(SubjectType.ORG_UNIT, memberships.parentUnits()).stream()
                    .filter(assignment -> assignment.getTerms().includeSubUnits()).forEach(found::add);
        }
        if (!memberships.positions().isEmpty()) {
            found.addAll(assignments.findBySubjects(SubjectType.POSITION, memberships.positions()));
        }
        Map<Long, Role> byId = roles.findAllById(found.stream().map(RoleAssignment::getRoleId).distinct().toList()).stream()
                .collect(Collectors.toMap(Role::requireId, role -> role));
        Map<Long, List<AssignmentView>> sources = views(found, now).stream()
                .collect(Collectors.groupingBy(AssignmentView::roleId, LinkedHashMap::new, Collectors.toList()));
        List<EffectiveRole> result = new ArrayList<>();
        for (Map.Entry<Long, List<AssignmentView>> entry : sources.entrySet()) {
            Role role = byId.get(entry.getKey());
            if (role != null) {
                result.add(effectiveRole(role, entry.getValue()));
            }
        }
        result.sort(Comparator.comparing((EffectiveRole role) -> !role.active()).thenComparing(role -> role.role().name()));
        return result;
    }

    private static EffectiveRole effectiveRole(Role role, List<AssignmentView> sources)
    {
        return new EffectiveRole(RoleView.from(role), sources, role.isEnabled() && sources.stream().anyMatch(AssignmentView::valid));
    }

    private List<AssignmentView> views(List<RoleAssignment> rows, Instant now)
    {
        Map<SubjectType, Map<Long, Subject>> names = new EnumMap<>(SubjectType.class);
        rows.stream().collect(Collectors.groupingBy(RoleAssignment::getSubjectType)).forEach((type, ofType) ->
                names.put(type, subjects.names(type, ofType.stream().map(RoleAssignment::getSubjectId).toList())));
        List<AssignmentView> result = new ArrayList<>();
        for (RoleAssignment row : rows) {
            Subject subject = names.getOrDefault(row.getSubjectType(), Map.of()).get(row.getSubjectId());
            if (subject != null) {
                result.add(view(row, subject, now));
            }
        }
        return result;
    }

    private static AssignmentView view(RoleAssignment assignment, Subject subject, Instant now)
    {
        return new AssignmentView(assignment.requireId(), assignment.getRoleId(), subject, assignment.getTerms(),
                assignment.isValidAt(now));
    }

    private void requireAssignable(long actorId, Role role)
    {
        if (role.getType() == RoleType.SYSTEM && SystemRole.PLATFORM_ADMIN.code().equals(role.getCode())
                && !platform.isPlatformAdministrator(actorId)) {
            throw new GrantForgeException(AuthzErrorCode.ROLE_NOT_ASSIGNABLE, "only platform administrators give " + role.getCode());
        }
    }

    private void requireUnprotected(Role role, RoleAssignment assignment)
    {
        if (role.getType() == RoleType.SYSTEM && assignment.getSubjectType() == SubjectType.USER
                && accounts.findById(assignment.getSubjectId()).map(UserAccount::isSystemAccount).orElse(false)) {
            throw new GrantForgeException(AuthzErrorCode.ASSIGNMENT_PROTECTED, "system account keeps " + role.getCode());
        }
    }

    private <T> T write(long actorId, Supplier<T> change)
    {
        access.requireTenantAdministrator(actorId);
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "assignment changed concurrently", race);
        }
    }

    private static RoleAssignment period(Supplier<RoleAssignment> change)
    {
        try {
            return change.get();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(AuthzErrorCode.ASSIGNMENT_PERIOD_INVALID, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    private Role requireRole(long id)
    {
        return roles.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id));
    }

    private RoleAssignment requireAssignment(long id)
    {
        return assignments.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no assignment " + id));
    }

    private void record(AuditAction action, long actorId, AssignmentView assignment)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(assignment.roleId()), assignment.subject().type() + ":" + assignment.subject().id()));
    }
}
