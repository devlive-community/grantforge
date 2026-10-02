// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Who has which role in the bound tenant: assignments to accounts, groups, departments and positions, and the
 * roles an account ends up with. Who may manage assignments is a matter of permissions, which the API checks;
 * no one gives a role beyond their own rights, and only holders of the platform administrator role give, change or
 * remove it. System accounts keep their
 * system roles. Every method must be called with the actor's tenant bound.
 */
@Service
public final class RoleAssignmentService
{
    private final RoleAssignmentRepository assignments;
    private final RoleRepository roles;
    private final UserAccountRepository accounts;
    private final SubjectDirectory subjects;
    private final EffectiveRoles effectiveRoles;
    private final AuthorizationEvaluator evaluator;
    private final RoleGrantRepository grants;
    private final ResourceRepository resources;
    private final ApplicationRepository applications;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param assignments assignments of the bound tenant
     * @param roles roles of the bound tenant
     * @param accounts accounts, to protect system accounts
     * @param subjects names subjects
     * @param effectiveRoles works out an account's roles
     * @param evaluator works out what accounts and roles allow, against escalation
     * @param grants grants of the bound tenant, to find the applications a role reaches
     * @param resources the resource catalog
     * @param applications applications, to find the console's own
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param clock the current time, for validity
     */
    public RoleAssignmentService(RoleAssignmentRepository assignments, RoleRepository roles, UserAccountRepository accounts,
            SubjectDirectory subjects, EffectiveRoles effectiveRoles, AuthorizationEvaluator evaluator, RoleGrantRepository grants,
            ResourceRepository resources, ApplicationRepository applications, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.assignments = requireNonNull(assignments, "assignments");
        this.roles = requireNonNull(roles, "roles");
        this.accounts = requireNonNull(accounts, "accounts");
        this.subjects = requireNonNull(subjects, "subjects");
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.grants = requireNonNull(grants, "grants");
        this.resources = requireNonNull(resources, "resources");
        this.applications = requireNonNull(applications, "applications");
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<AssignmentView> list(long actorId, long roleId)
    {
        return requireNonNull(transactions.execute(status -> {
            requireRole(roleId);
            return effectiveRoles.views(assignments.findByRole(roleId), clock.instant());
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown role or subject, {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE}, {@link AuthzErrorCode#ROLE_EXCEEDS_ACTOR},
     *         {@link AuthzErrorCode#ASSIGNMENT_EXISTS}
     *         or {@link AuthzErrorCode#ASSIGNMENT_PERIOD_INVALID}
     */
    public AssignmentView assign(long actorId, long roleId, SubjectType type, long subjectId, RoleAssignment.Terms terms)
    {
        requireNonNull(type, "type");
        AssignmentView view = write(() -> {
            Role role = requireRole(roleId);
            requireAssignable(actorId, role);
            requireWithinActor(actorId, role);
            Subject subject = subjects.find(type, subjectId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no " + type + " " + subjectId));
            if (assignments.findByRoleIdAndSubjectTypeAndSubjectId(roleId, type, subjectId).isPresent()) {
                throw new GrantForgeException(AuthzErrorCode.ASSIGNMENT_EXISTS, type + " " + subjectId + " has role " + roleId);
            }
            RoleAssignment assignment = assignments.saveAndFlush(period(() -> RoleAssignment.create(roleId, type, subjectId, terms)));
            return EffectiveRoles.view(assignment, subject, clock.instant());
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE}, {@link AuthzErrorCode#ASSIGNMENT_PROTECTED} or
     *         {@link AuthzErrorCode#ASSIGNMENT_PERIOD_INVALID}
     */
    public AssignmentView change(long actorId, long id, RoleAssignment.Terms terms)
    {
        AssignmentView view = write(() -> {
            RoleAssignment assignment = requireAssignment(id);
            Role role = requireRole(assignment.getRoleId());
            requireAssignable(actorId, role);
            requireUnprotected(role, assignment);
            period(() -> {
                assignment.change(terms);
                return assignment;
            });
            assignments.saveAndFlush(assignment);
            return effectiveRoles.views(List.of(assignment), clock.instant()).get(0);
        });
        record(AuditAction.ROLE_ASSIGNMENT_CHANGED, actorId, view);
        return view;
    }

    /**
     * Takes a role from a subject.
     *
     * @param actorId the account asking
     * @param id the assignment
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE} or {@link AuthzErrorCode#ASSIGNMENT_PROTECTED}
     */
    public void remove(long actorId, long id)
    {
        AssignmentView view = write(() -> {
            RoleAssignment assignment = requireAssignment(id);
            Role role = requireRole(assignment.getRoleId());
            requireAssignable(actorId, role);
            requireUnprotected(role, assignment);
            AssignmentView removed = effectiveRoles.views(List.of(assignment), clock.instant()).stream().findFirst()
                    .orElseGet(() -> EffectiveRoles.view(assignment, new Subject(assignment.getSubjectType(), assignment.getSubjectId(),
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<EffectiveRole> rolesOf(long actorId, long accountId)
    {
        return requireNonNull(transactions.execute(status -> {
            if (!accounts.existsById(accountId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no account " + accountId);
            }
            return effectiveRoles.of(accountId, clock.instant());
        }));
    }

    private void requireAssignable(long actorId, Role role)
    {
        if (role.getType() == RoleType.SYSTEM && SystemRole.PLATFORM_ADMIN.code().equals(role.getCode())
                && effectiveRoles.of(actorId, clock.instant()).stream().noneMatch(held -> held.active()
                && held.role().id() == role.requireId())) {
            throw new GrantForgeException(AuthzErrorCode.ROLE_NOT_ASSIGNABLE, "only platform administrators give " + role.getCode());
        }
    }

    /** Giving a role must not hand out more than the actor has: what the role allows must be within the actor's rights. */
    private void requireWithinActor(long actorId, Role role)
    {
        Set<Long> applicationIds = new HashSet<>(resources.findAllById(grants.findByRoleId(role.requireId()).stream()
                .map(RoleGrant::getResourceId).toList()).stream().map(Resource::getApplicationId).toList());
        if (role.getType() == RoleType.SYSTEM) {
            applications.findByCode(Application.CONSOLE).ifPresent(console -> applicationIds.add(console.requireId()));
        }
        for (long applicationId : applicationIds) {
            if (!evaluator.usableResources(actorId, applicationId).containsAll(evaluator.coveredBy(List.of(RoleView.from(role)),
                    applicationId))) {
                throw new GrantForgeException(AuthzErrorCode.ROLE_EXCEEDS_ACTOR, "role " + role.getCode() + " exceeds account "
                        + actorId);
            }
        }
    }

    private void requireUnprotected(Role role, RoleAssignment assignment)
    {
        if (role.getType() == RoleType.SYSTEM && assignment.getSubjectType() == SubjectType.USER
                && accounts.findById(assignment.getSubjectId()).map(UserAccount::isSystemAccount).orElse(false)) {
            throw new GrantForgeException(AuthzErrorCode.ASSIGNMENT_PROTECTED, "system account keeps " + role.getCode());
        }
    }

    private <T> T write(Supplier<T> change)
    {
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
