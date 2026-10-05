// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Which rows of a secured entity a role may read, change, delete or export, or may not: a scope, with a checked condition
 * for {@link DataScope#CONDITION} and department ids for {@link DataScope#CUSTOM_ORGS}.
 */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_data_policy")
public class DataPolicy
        extends TenantScopedEntity
{
    /** Longest stored condition: the portable column holds 2000 characters on every database (authz-0011). */
    public static final int CONDITION_MAX = 2000;

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    @Column(name = "entity_code", nullable = false, updatable = false, length = 64)
    private String entityCode = "";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action_name", nullable = false, length = 16)
    private DataAction action = DataAction.READ;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "data_scope", nullable = false, length = 24)
    private DataScope scope = DataScope.SELF;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "effect", nullable = false, length = 8)
    private GrantEffect effect = GrantEffect.ALLOW;

    @Column(name = "condition_text", length = CONDITION_MAX)
    private @Nullable String condition;

    @Column(name = "org_unit_ids", length = 1000)
    private @Nullable String orgUnitIds;

    /** For JPA. */
    protected DataPolicy()
    {
    }

    /**
     * Creates a policy.
     *
     * @param roleId the role
     * @param entityCode the secured entity
     * @return the policy; describe it before saving
     */
    public static DataPolicy create(long roleId, String entityCode)
    {
        DataPolicy policy = new DataPolicy();
        policy.roleId = roleId;
        policy.entityCode = requireNonNull(entityCode, "entityCode");
        return policy;
    }

    /**
     * Sets what the policy says.
     *
     * @param newAction what it lets the role do
     * @param newScope which rows
     * @param newEffect whether it allows or denies
     * @param newCondition the condition as checked JSON, for {@link DataScope#CONDITION}; {@code null} otherwise
     * @param newOrgUnitIds the department ids as a JSON array, for {@link DataScope#CUSTOM_ORGS}; {@code null} otherwise
     */
    public void describe(DataAction newAction, DataScope newScope, GrantEffect newEffect, @Nullable String newCondition,
            @Nullable String newOrgUnitIds)
    {
        this.action = requireNonNull(newAction, "action");
        this.scope = requireNonNull(newScope, "scope");
        this.effect = requireNonNull(newEffect, "effect");
        this.condition = newCondition;
        this.orgUnitIds = newOrgUnitIds;
    }

    /**
     * Copies the policy to another role, such as a copy of its role.
     *
     * @param otherRoleId the other role
     * @return the copy
     */
    public DataPolicy copyTo(long otherRoleId)
    {
        DataPolicy copy = create(otherRoleId, entityCode);
        copy.describe(action, scope, effect, condition, orgUnitIds);
        return copy;
    }

    /**
     * Returns the role.
     *
     * @return the role's id
     */
    public long getRoleId()
    {
        return roleId;
    }

    /**
     * Returns the secured entity.
     *
     * @return its code
     */
    public String getEntityCode()
    {
        return entityCode;
    }

    /**
     * Returns what the policy lets the role do.
     *
     * @return the action
     */
    public DataAction getAction()
    {
        return action;
    }

    /**
     * Returns which rows the policy covers.
     *
     * @return the scope
     */
    public DataScope getScope()
    {
        return scope;
    }

    /**
     * Returns whether the policy allows or denies.
     *
     * @return the effect
     */
    public GrantEffect getEffect()
    {
        return effect;
    }

    /**
     * Returns the condition.
     *
     * @return checked JSON, or {@code null}
     */
    public @Nullable String getCondition()
    {
        return condition;
    }

    /**
     * Returns the chosen departments.
     *
     * @return a JSON array of ids, or {@code null}
     */
    public @Nullable String getOrgUnitIds()
    {
        return orgUnitIds;
    }
}
