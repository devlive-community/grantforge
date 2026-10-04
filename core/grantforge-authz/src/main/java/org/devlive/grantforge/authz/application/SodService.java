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
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SodConstraint;
import org.devlive.grantforge.authz.domain.SodConstraintRepository;
import org.devlive.grantforge.authz.domain.SodConstraintRole;
import org.devlive.grantforge.authz.domain.SodConstraintRoleRepository;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Separation of duties (D-73): constraints that no account may hold more than some of a set of roles, and the
 * conflicts with them. An account holds a role through any active assignment (directly, through a group, department or
 * position) and also every role that role inherits from. Role assignments and inheritance links check the accounts they
 * reach before and after the change, so a change that creates a conflict with an enforced constraint is refused while
 * conflicts that existed before do not block unrelated changes; the report lists every conflict. Every method must be
 * called with the tenant bound.
 */
@Service
public class SodService
{
    /** Codes: lowercase letters, digits and hyphens, starting with a letter. */
    public static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    /** The most roles a constraint may name. */
    static final int MAX_ROLES = 50;

    private final SodConstraintRepository constraints;
    private final SodConstraintRoleRepository constraintRoles;
    private final RoleRepository roles;
    private final RoleParentRepository parents;
    private final EffectiveRoles effectiveRoles;
    private final RoleHolders holders;
    private final SubjectDirectory subjects;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param constraints the constraints
     * @param constraintRoles their roles
     * @param roles the roles
     * @param parents the inheritance links
     * @param effectiveRoles works out the roles an account holds
     * @param holders finds the accounts that hold roles
     * @param subjects names accounts
     * @param audit records changes of constraints
     * @param transactionManager opens transactions
     * @param clock the current time, for the validity of assignments
     */
    public SodService(SodConstraintRepository constraints, SodConstraintRoleRepository constraintRoles, RoleRepository roles,
            RoleParentRepository parents, EffectiveRoles effectiveRoles, RoleHolders holders, SubjectDirectory subjects, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.constraints = requireNonNull(constraints, "constraints");
        this.constraintRoles = requireNonNull(constraintRoles, "constraintRoles");
        this.roles = requireNonNull(roles, "roles");
        this.parents = requireNonNull(parents, "parents");
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.holders = requireNonNull(holders, "holders");
        this.subjects = requireNonNull(subjects, "subjects");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists the constraints by name.
     *
     * @return the constraints
     */
    public List<SodConstraintView> list()
    {
        return requireNonNull(transactions.execute(status -> views(constraints.findAllByOrderByNameAsc())));
    }

    /**
     * Adds a constraint.
     *
     * @param actorId the administrator
     * @param command the constraint
     * @return the constraint
     * @throws GrantForgeException with {@link AuthzErrorCode#SOD_CONSTRAINT_INVALID}, {@link AuthzErrorCode#SOD_CODE_TAKEN}
     *         or {@link CommonErrorCode#NOT_FOUND} for an unknown role
     */
    public SodConstraintView create(long actorId, SodConstraintCommand command)
    {
        String code = Strings.blankToNull(command.code());
        if (code == null || !CODE.matcher(code).matches()) {
            throw invalid("code must be 2-64 lowercase letters, digits or hyphens, starting with a letter");
        }
        try {
            SodConstraintView created = requireNonNull(transactions.execute(status -> {
                if (constraints.findByCode(code).isPresent()) {
                    throw taken(code, null);
                }
                SodConstraint constraint = SodConstraint.create(code);
                configure(constraint, command);
                long id = constraints.saveAndFlush(constraint).requireId();
                store(id, command.roleIds());
                return views(List.of(constraint)).get(0);
            }));
            record(AuditAction.SOD_CONSTRAINT_CREATED, actorId, created);
            return created;
        }
        catch (DataIntegrityViolationException concurrent) {
            throw taken(code, concurrent);
        }
    }

    /**
     * Changes a constraint; its code stays. Accounts that conflict with it already are not touched: the report lists them.
     *
     * @param actorId the administrator
     * @param id the constraint
     * @param command the new values
     * @return the constraint
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#SOD_CONSTRAINT_INVALID}
     */
    public SodConstraintView update(long actorId, long id, SodConstraintCommand command)
    {
        SodConstraintView updated = requireNonNull(transactions.execute(status -> {
            SodConstraint constraint = require(id);
            configure(constraint, command);
            constraintRoles.deleteByConstraint(id);
            store(id, command.roleIds());
            return views(List.of(constraint)).get(0);
        }));
        record(AuditAction.SOD_CONSTRAINT_UPDATED, actorId, updated);
        return updated;
    }

    /**
     * Deletes a constraint.
     *
     * @param actorId the administrator
     * @param id the constraint
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long id)
    {
        SodConstraintView deleted = requireNonNull(transactions.execute(status -> {
            SodConstraint constraint = require(id);
            SodConstraintView view = views(List.of(constraint)).get(0);
            constraintRoles.deleteByConstraint(id);
            constraints.delete(constraint);
            return view;
        }));
        record(AuditAction.SOD_CONSTRAINT_DELETED, actorId, deleted);
    }

    /**
     * Lists every account that holds more of an enabled constraint's roles than it allows, whatever the constraint's mode.
     *
     * @return the conflicts by constraint and account name
     */
    public List<SodConflict> conflicts()
    {
        return requireNonNull(transactions.execute(status -> {
            List<SodConstraintView> enabled = views(constraints.findByEnabledTrue());
            if (enabled.isEmpty()) {
                return List.of();
            }
            Instant now = clock.instant();
            RoleHierarchy hierarchy = new RoleHierarchy(parents.findAll());
            // Holding a role that inherits from a constrained role means holding that role too.
            Set<Long> reaching = new HashSet<>();
            for (SodConstraintView constraint : enabled) {
                for (RoleView role : constraint.roles()) {
                    reaching.add(role.id());
                    reaching.addAll(hierarchy.descendants(role.id()).keySet());
                }
            }
            Set<Long> accounts = holders.of(reaching, now);
            return conflictsOf(accounts, enabled, hierarchy, now).stream()
                    .sorted(Comparator.comparing((SodConflict conflict) -> conflict.constraint().name()).thenComparing(conflict -> conflict.account().name()))
                    .toList();
        }));
    }

    /**
     * Notes the conflicts with enforced constraints that some accounts have now, before a change that could add more.
     * Call it in the transaction of the change, then {@link #requireNoNewConflicts} after it.
     *
     * @param accounts the accounts the change reaches
     * @return what to compare with afterwards
     */
    public Guard guard(Collection<Long> accounts)
    {
        List<SodConstraintView> enforced = views(constraints.findByEnabledTrue()).stream().filter(view -> view.mode() == SodMode.ENFORCE).toList();
        Set<Long> reached = Set.copyOf(accounts);
        if (enforced.isEmpty() || reached.isEmpty()) {
            return new Guard(reached, enforced, Set.of());
        }
        Instant now = clock.instant();
        Set<String> before = conflictsOf(reached, enforced, new RoleHierarchy(parents.findAll()), now).stream().map(SodService::key)
                .collect(Collectors.toSet());
        return new Guard(reached, enforced, before);
    }

    /**
     * Refuses a change that made an account conflict with an enforced constraint it did not conflict with before; the
     * transaction then rolls the change back.
     *
     * @param guard what {@link #guard} noted before the change
     * @throws GrantForgeException with {@link AuthzErrorCode#SOD_CONFLICT}, naming the first new conflict
     */
    public void requireNoNewConflicts(Guard guard)
    {
        if (guard.enforced().isEmpty() || guard.accounts().isEmpty()) {
            return;
        }
        List<SodConflict> created = conflictsOf(guard.accounts(), guard.enforced(), new RoleHierarchy(parents.findAll()), clock.instant())
                .stream().filter(conflict -> !guard.before().contains(key(conflict))).toList();
        if (!created.isEmpty()) {
            SodConflict first = created.get(0);
            String held = first.roles().stream().map(RoleView::name).collect(Collectors.joining(", "));
            throw new GrantForgeException(AuthzErrorCode.SOD_CONFLICT, created.size() + " new separation-of-duties conflicts", first.account().name(),
                    held, first.constraint().name());
        }
    }

    /**
     * Returns the accounts an assignment to a subject reaches, for {@link #guard}.
     *
     * @param type what the subject is
     * @param subjectId the subject
     * @param includeSubUnits whether an assignment to a department reaches its sub-departments
     * @return the accounts
     */
    public Set<Long> reachedBy(SubjectType type, long subjectId, boolean includeSubUnits)
    {
        return holders.ofSubject(type, subjectId, includeSubUnits);
    }

    /**
     * Returns the accounts holding a role or one that inherits from it, for {@link #guard} before its parents change.
     *
     * @param roleId the role
     * @return the accounts
     */
    public Set<Long> holdingOrInheriting(long roleId)
    {
        Set<Long> reaching = new HashSet<>(new RoleHierarchy(parents.findAll()).descendants(roleId).keySet());
        reaching.add(roleId);
        return holders.of(reaching, clock.instant());
    }

    private List<SodConflict> conflictsOf(Collection<Long> accounts, List<SodConstraintView> applying, RoleHierarchy hierarchy, Instant now)
    {
        List<SodConflict> found = new ArrayList<>();
        Map<Long, Subject> names = subjects.names(SubjectType.USER, accounts);
        for (long accountId : accounts) {
            Set<Long> held = held(accountId, hierarchy, now);
            Subject account = names.get(accountId);
            if (account == null) {
                continue;
            }
            for (SodConstraintView constraint : applying) {
                List<RoleView> overlap = constraint.roles().stream().filter(role -> held.contains(role.id())).toList();
                if (overlap.size() > constraint.maxRoles()) {
                    found.add(new SodConflict(constraint, account, overlap));
                }
            }
        }
        return found;
    }

    private Set<Long> held(long accountId, RoleHierarchy hierarchy, Instant now)
    {
        Set<Long> held = new HashSet<>();
        for (EffectiveRole role : effectiveRoles.of(accountId, now)) {
            if (role.active()) {
                held.add(role.role().id());
                held.addAll(hierarchy.ancestors(role.role().id()).keySet());
            }
        }
        return held;
    }

    private void configure(SodConstraint constraint, SodConstraintCommand command)
    {
        String name = Strings.blankToNull(command.name());
        if (name == null || name.length() > 128) {
            throw invalid("name is required and at most 128 characters");
        }
        String description = Strings.blankToNull(command.description());
        if (description != null && description.length() > 512) {
            throw invalid("description is at most 512 characters");
        }
        Set<Long> roleIds = command.roleIds();
        if (roleIds.size() < 2 || roleIds.size() > MAX_ROLES) {
            throw invalid("a constraint needs 2-" + MAX_ROLES + " roles");
        }
        if (command.maxRoles() < 1 || command.maxRoles() >= roleIds.size()) {
            throw invalid("maxRoles must be at least 1 and less than the number of roles");
        }
        Set<Long> known = roles.findAllById(roleIds).stream().map(Role::requireId).collect(Collectors.toSet());
        for (long roleId : roleIds) {
            if (!known.contains(roleId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + roleId);
            }
        }
        constraint.configure(name, description, command.maxRoles(), command.mode(), command.enabled());
    }

    private void store(long constraintId, Set<Long> roleIds)
    {
        constraintRoles.saveAll(new LinkedHashSet<>(roleIds).stream().map(roleId -> SodConstraintRole.of(constraintId, roleId)).toList());
        constraintRoles.flush();
    }

    private List<SodConstraintView> views(List<SodConstraint> found)
    {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> roleIds = new HashMap<>();
        for (SodConstraintRole link : constraintRoles.findByConstraintIdIn(found.stream().map(SodConstraint::requireId).toList())) {
            roleIds.computeIfAbsent(link.getConstraintId(), key -> new ArrayList<>()).add(link.getRoleId());
        }
        Map<Long, RoleView> named = roles.findAllById(roleIds.values().stream().flatMap(List::stream).distinct().toList()).stream()
                .map(RoleView::from).collect(Collectors.toMap(RoleView::id, Function.identity()));
        return found.stream().map(constraint -> new SodConstraintView(constraint.requireId(), constraint.getCode(), constraint.getName(),
                constraint.getDescription(), roleIds.getOrDefault(constraint.requireId(), List.of()).stream().map(named::get)
                        .filter(Objects::nonNull).sorted(Comparator.comparing(RoleView::name)).toList(),
                constraint.getMaxRoles(), constraint.getMode(), constraint.isEnabled())).toList();
    }

    private SodConstraint require(long id)
    {
        return constraints.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no constraint " + id));
    }

    private void record(AuditAction action, long actorId, SodConstraintView constraint)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null, constraint.code(),
                constraint.roles().stream().map(RoleView::code).collect(Collectors.joining(","))));
    }

    private static String key(SodConflict conflict)
    {
        return conflict.constraint().id() + ":" + conflict.account().id();
    }

    private static GrantForgeException invalid(String reason)
    {
        return new GrantForgeException(AuthzErrorCode.SOD_CONSTRAINT_INVALID, "invalid constraint: " + reason, reason);
    }

    private static GrantForgeException taken(String code, @Nullable Throwable cause)
    {
        return new GrantForgeException(AuthzErrorCode.SOD_CODE_TAKEN, "constraint code taken: " + code, cause, code);
    }

    /**
     * What a change's accounts conflicted with before it.
     *
     * @param accounts the accounts the change reaches
     * @param enforced the enforced constraints
     * @param before the conflicts they had, as constraint and account
     */
    public record Guard(Set<Long> accounts, List<SodConstraintView> enforced, Set<String> before)
    {
        /** Copies the values. */
        public Guard
        {
            accounts = Set.copyOf(accounts);
            enforced = List.copyOf(enforced);
            before = Set.copyOf(before);
        }
    }
}
