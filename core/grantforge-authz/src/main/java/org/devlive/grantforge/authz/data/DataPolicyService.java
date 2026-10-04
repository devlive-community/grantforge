// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.application.AuthzErrorCode;
import org.devlive.grantforge.authz.domain.DataPolicy;
import org.devlive.grantforge.authz.domain.DataPolicyRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * The data policies of the bound tenant's roles: listing, adding, changing and removing them. A policy must name a secured
 * entity and a scope the entity supports; the scope of every tenant only exists in the platform tenant; conditions are
 * checked against the entity's filterable fields and chosen departments must exist. Every method must be called with the
 * actor's tenant bound.
 */
@Service
public final class DataPolicyService
{
    /** Most departments one policy chooses. */
    public static final int MAX_ORG_UNITS = 50;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Long>> IDS = new TypeReference<>()
    {
    };

    private final DataPolicyRepository policies;
    private final RoleRepository roles;
    private final OrgUnitRepository units;
    private final TenantRepository tenants;
    private final DataEntities entities;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param policies the data policies
     * @param roles the roles of the bound tenant
     * @param units the departments, to check chosen ones
     * @param tenants the tenants, to tell the platform tenant
     * @param entities the secured entities
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public DataPolicyService(DataPolicyRepository policies, RoleRepository roles, OrgUnitRepository units, TenantRepository tenants,
            DataEntities entities, AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.policies = requireNonNull(policies, "policies");
        this.roles = requireNonNull(roles, "roles");
        this.units = requireNonNull(units, "units");
        this.tenants = requireNonNull(tenants, "tenants");
        this.entities = requireNonNull(entities, "entities");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Lists the data policies of a role.
     *
     * @param roleId the role
     * @return the policies, by entity
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<DataPolicyView> list(long roleId)
    {
        return requireNonNull(transactions.execute(status -> {
            requireRole(roleId);
            return policies.findByRoleIdOrderByEntityCodeAscIdAsc(roleId).stream().map(DataPolicyService::view).toList();
        }));
    }

    /**
     * Adds a data policy to a role.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param command the policy
     * @return the policy
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#DATA_POLICY_INVALID}
     *         listing the inputs at fault
     */
    public DataPolicyView create(long actorId, long roleId, DataPolicyCommand command)
    {
        DataPolicyView created = audited(() -> write(() -> {
            requireRole(roleId);
            Checked checked = check(command.entityCode(), command);
            DataPolicy policy = DataPolicy.create(roleId, command.entityCode());
            policy.describe(command.action(), command.scope(), command.effect(), checked.condition(), checked.orgUnitIds());
            return view(policies.saveAndFlush(policy));
        }), made -> record(AuditAction.DATA_POLICY_CREATED, actorId, made));
        return created;
    }

    /**
     * Changes a data policy; its role and entity stay.
     *
     * @param actorId the account asking
     * @param id the policy
     * @param command the policy
     * @return the policy
     * @throws GrantForgeException as {@link #create}
     */
    public DataPolicyView update(long actorId, long id, DataPolicyCommand command)
    {
        DataPolicyView updated = audited(() -> write(() -> {
            DataPolicy policy = require(id);
            Checked checked = check(policy.getEntityCode(), command);
            policy.describe(command.action(), command.scope(), command.effect(), checked.condition(), checked.orgUnitIds());
            return view(policies.saveAndFlush(policy));
        }), made -> record(AuditAction.DATA_POLICY_UPDATED, actorId, made));
        return updated;
    }

    /**
     * Removes a data policy.
     *
     * @param actorId the account asking
     * @param id the policy
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long id)
    {
        audited(() -> write(() -> {
            DataPolicy policy = require(id);
            DataPolicyView view = view(policy);
            policies.delete(policy);
            return view;
        }), made -> record(AuditAction.DATA_POLICY_DELETED, actorId, made));
    }

    /** Checks a policy; returns its condition and departments as stored. */
    private Checked check(String entityCode, DataPolicyCommand command)
    {
        List<FieldIssue> issues = new ArrayList<>();
        SecuredEntityDefinition entity = entities.find(entityCode).orElse(null);
        if (entity == null) {
            throw new GrantForgeException(AuthzErrorCode.DATA_POLICY_INVALID, "no secured entity " + entityCode)
                    .withFieldIssues(List.of(FieldIssue.of("entityCode", "error.data.entity-unknown", entityCode)));
        }
        DataScope scope = command.scope();
        if (!entity.scopes().contains(scope)) {
            issues.add(FieldIssue.of("scope", "error.data.scope-not-for-entity"));
        }
        else if (scope == DataScope.ALL && !tenants.findById(TenantContext.requireTenantId()).map(Tenant::isPlatform).orElse(false)) {
            issues.add(FieldIssue.of("scope", "error.data.scope-platform-only"));
        }
        String condition = null;
        JsonNode given = command.condition();
        if (scope == DataScope.CONDITION) {
            if (given == null) {
                issues.add(FieldIssue.of("condition", "error.data.condition-required"));
            }
            else {
                ConditionCodec.Result read = ConditionCodec.read(given, entity, "condition");
                issues.addAll(read.issues());
                Condition parsed = read.condition();
                if (parsed != null) {
                    condition = ConditionCodec.write(parsed);
                }
            }
        }
        else if (given != null && !given.isNull()) {
            issues.add(FieldIssue.of("condition", "error.data.condition-not-for-scope"));
        }
        String orgUnitIds = null;
        if (scope == DataScope.CUSTOM_ORGS) {
            List<Long> ids = command.orgUnitIds().stream().distinct().toList();
            if (ids.isEmpty() || ids.size() > MAX_ORG_UNITS) {
                issues.add(FieldIssue.of("orgUnitIds", "error.data.org-units-count", MAX_ORG_UNITS));
            }
            else if (units.findAllById(ids).size() != ids.size()) {
                issues.add(FieldIssue.of("orgUnitIds", "error.data.org-units-unknown"));
            }
            orgUnitIds = JSON.writeValueAsString(ids.stream().sorted().toList());
        }
        else if (!command.orgUnitIds().isEmpty()) {
            issues.add(FieldIssue.of("orgUnitIds", "error.data.org-units-not-for-scope"));
        }
        if (!issues.isEmpty()) {
            throw new GrantForgeException(AuthzErrorCode.DATA_POLICY_INVALID, issues.size() + " data policy issues").withFieldIssues(issues);
        }
        return new Checked(condition, orgUnitIds);
    }

    private Role requireRole(long id)
    {
        return roles.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id));
    }

    private DataPolicy require(long id)
    {
        return policies.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no data policy " + id));
    }

    private static DataPolicyView view(DataPolicy policy)
    {
        return new DataPolicyView(policy.requireId(), policy.getRoleId(), policy.getEntityCode(), policy.getAction(), policy.getScope(),
                policy.getEffect(), policy.getCondition(), ids(policy.getOrgUnitIds()),
                requireNonNullElse(policy.getUpdatedAt(), Instant.EPOCH));
    }

    private static List<Long> ids(@Nullable String json)
    {
        if (json == null) {
            return List.of();
        }
        try {
            return List.copyOf(new HashSet<>(JSON.readValue(json, IDS))).stream().sorted().toList();
        }
        catch (JacksonException broken) {
            throw new IllegalStateException("stored department ids are not valid JSON", broken);
        }
    }

    private <T> T write(Supplier<T> change)
    {
        return requireNonNull(transactions.execute(status -> change.get()));
    }

    private void record(AuditAction action, long actorId, DataPolicyView policy)
    {
        audit.recordWithChange(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(policy.id()), "role " + policy.roleId() + ": " + policy.entityCode() + " " + policy.action()));
    }

    /** A policy's condition and departments as stored. */
    private record Checked(@Nullable String condition, @Nullable String orgUnitIds)
    {
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
