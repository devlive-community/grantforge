// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.application.AuthzErrorCode;
import org.devlive.grantforge.authz.domain.FieldPolicy;
import org.devlive.grantforge.authz.domain.FieldPolicyRepository;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The field policies of the bound tenant's roles: how holders of a role see and change the secured fields. A role's
 * policies are replaced as a whole. Callers need the matching permission, which the API checks. Every method must be called
 * with the actor's tenant bound.
 */
@Service
public final class FieldPolicyService
{
    /** Most field policies a role has. */
    public static final int MAX_POLICIES = 200;

    private final FieldPolicyRepository policies;
    private final RoleRepository roles;
    private final ResourceRepository resources;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param policies the field policies
     * @param roles the roles
     * @param resources the catalog, which lists the secured fields
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public FieldPolicyService(FieldPolicyRepository policies, RoleRepository roles, ResourceRepository resources, AuditLog audit,
            PlatformTransactionManager transactionManager)
    {
        this.policies = requireNonNull(policies, "policies");
        this.roles = requireNonNull(roles, "roles");
        this.resources = requireNonNull(resources, "resources");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Returns a role's field policies by entity and field.
     *
     * @param roleId the role
     * @return the policies
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown role
     */
    public List<FieldPolicyView> list(long roleId)
    {
        return requireNonNull(transactions.execute(status -> {
            requireRole(roleId);
            return policies.findByRoleIdOrderByEntityCodeAscFieldCodeAsc(roleId).stream().map(FieldPolicyView::from).toList();
        }));
    }

    /**
     * Replaces a role's field policies.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param commands the new policies, one per field at most
     * @return the policies
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown role, or
     *         {@link AuthzErrorCode#FIELD_POLICY_INVALID} with an issue per wrong policy
     */
    public List<FieldPolicyView> replace(long actorId, long roleId, List<FieldPolicyCommand> commands)
    {
        List<FieldPolicyView> replaced = audited(() -> requireNonNull(transactions.execute(status -> {
            requireRole(roleId);
            check(commands);
            policies.removeRole(roleId);
            policies.saveAll(commands.stream().map(command -> FieldPolicy.create(roleId, command.entityCode(), command.fieldCode(),
                    command.readMode(), command.maskStrategy(), command.writeMode())).toList());
            policies.flush();
            return policies.findByRoleIdOrderByEntityCodeAscFieldCodeAsc(roleId).stream().map(FieldPolicyView::from).toList();
        })), made -> audit.recordWithChange(new AuditRecord(AuditAction.FIELD_POLICIES_CHANGED, AuditOutcome.SUCCESS,
                TenantContext.requireTenantId(), actorId, null, Long.toString(roleId), made.size() + " fields")));
        return replaced;
    }

    private void check(List<FieldPolicyCommand> commands)
    {
        if (commands.size() > MAX_POLICIES) {
            throw invalid(List.of(FieldIssue.of("policies", "error.field.too-many", MAX_POLICIES)));
        }
        Set<String> codes = commands.stream().map(FieldPolicyService::code).collect(Collectors.toSet());
        Set<String> known = codes.isEmpty() ? Set.of() : resources.findByTypeAndCodeIn(ResourceType.FIELD, codes).stream()
                .filter(Resource::isBuiltin).map(Resource::getCode).collect(Collectors.toSet());
        List<FieldIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < commands.size(); i++) {
            FieldPolicyCommand command = commands.get(i);
            String path = "policies[" + i + "]";
            String code = code(command);
            if (!known.contains(code)) {
                issues.add(FieldIssue.of(path + ".fieldCode", "error.field.unknown", code));
            }
            else if (!seen.add(code)) {
                issues.add(FieldIssue.of(path + ".fieldCode", "error.field.duplicate"));
            }
            boolean masked = command.readMode() == FieldReadMode.MASKED;
            if (masked && command.maskStrategy() == null) {
                issues.add(FieldIssue.of(path + ".maskStrategy", "error.field.mask-required"));
            }
            else if (!masked && command.maskStrategy() != null) {
                issues.add(FieldIssue.of(path + ".maskStrategy", "error.field.mask-unexpected"));
            }
        }
        if (!issues.isEmpty()) {
            throw invalid(issues);
        }
    }

    private static String code(FieldPolicyCommand command)
    {
        return new DeclaredField(command.entityCode(), command.fieldCode(), command.fieldCode()).resourceCode();
    }

    private static GrantForgeException invalid(List<FieldIssue> issues)
    {
        return new GrantForgeException(AuthzErrorCode.FIELD_POLICY_INVALID, issues.size() + " field policy issues").withFieldIssues(issues);
    }

    private void requireRole(long id)
    {
        if (roles.findById(id).isEmpty()) {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id);
        }
    }

    /** Makes a change and records its event in one transaction, so neither happens without the other. */
    private <T> T audited(Supplier<T> change, Consumer<T> event)
    {
        return requireNonNull(transactions.execute(status -> {
            T made = change.get();
            event.accept(made);
            return made;
        }));
    }
}
