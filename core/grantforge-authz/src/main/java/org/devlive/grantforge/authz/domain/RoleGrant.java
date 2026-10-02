// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * That a role explicitly allows or denies a resource of the catalog, optionally until a time. Only explicit grants
 * are stored; what they imply (ancestors to see them, required dependencies) is derived ({@link GrantDerivation}).
 */
@Entity
@Table(name = "gf_role_grant")
public class RoleGrant
        extends TenantScopedEntity
{
    /** Types that can be granted; modules follow from what is granted below them. */
    public static final Set<ResourceType> GRANTABLE = Set.of(ResourceType.MENU, ResourceType.PAGE, ResourceType.TAB,
            ResourceType.ACTION, ResourceType.API);

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private long resourceId;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "effect", nullable = false, length = 8)
    private GrantEffect effect = GrantEffect.ALLOW;

    @Column(name = "expires_at")
    private @Nullable Instant expiresAt;

    @Column(name = "granted_by", nullable = false)
    private long grantedBy;

    /** For JPA. */
    protected RoleGrant()
    {
    }

    /**
     * Creates a grant.
     *
     * @param roleId the role
     * @param resource the resource
     * @param effect allow or deny
     * @param expiresAt when it stops applying, or {@code null} for never
     * @param grantedBy the account granting it
     * @return the grant
     * @throws IllegalArgumentException if the resource's type cannot be granted
     */
    public static RoleGrant create(long roleId, Resource resource, GrantEffect effect, @Nullable Instant expiresAt, long grantedBy)
    {
        if (!GRANTABLE.contains(resource.getType())) {
            throw new IllegalArgumentException(resource.getType() + " resources cannot be granted");
        }
        RoleGrant grant = new RoleGrant();
        grant.roleId = roleId;
        grant.resourceId = resource.requireId();
        grant.change(effect, expiresAt, grantedBy);
        return grant;
    }

    /**
     * Changes the effect and expiry.
     *
     * @param newEffect allow or deny
     * @param newExpiresAt when it stops applying, or {@code null} for never
     * @param actor the account changing it
     */
    public final void change(GrantEffect newEffect, @Nullable Instant newExpiresAt, long actor)
    {
        effect = requireNonNull(newEffect, "newEffect");
        expiresAt = newExpiresAt;
        grantedBy = actor;
    }

    /**
     * Copies the grant to another role.
     *
     * @param otherRoleId the role receiving the copy
     * @param actor the account copying it
     * @return the copy
     */
    public RoleGrant copyTo(long otherRoleId, long actor)
    {
        RoleGrant copy = new RoleGrant();
        copy.roleId = otherRoleId;
        copy.resourceId = resourceId;
        copy.change(effect, expiresAt, actor);
        return copy;
    }

    /**
     * Returns whether the grant still applies.
     *
     * @param now the current time
     * @return {@code true} until its expiry (exclusive)
     */
    public boolean appliesAt(Instant now)
    {
        Instant end = expiresAt;
        return end == null || now.isBefore(end);
    }

    /**
     * Returns the role.
     *
     * @return the role ID
     */
    public long getRoleId()
    {
        return roleId;
    }

    /**
     * Returns the resource.
     *
     * @return the resource ID
     */
    public long getResourceId()
    {
        return resourceId;
    }

    /**
     * Returns whether it allows or denies.
     *
     * @return the effect
     */
    public GrantEffect getEffect()
    {
        return effect;
    }

    /**
     * Returns when it stops applying.
     *
     * @return the expiry, or {@code null} for never
     */
    public @Nullable Instant getExpiresAt()
    {
        return expiresAt;
    }

    /**
     * Returns who last set it.
     *
     * @return the account ID
     */
    public long getGrantedBy()
    {
        return grantedBy;
    }
}
