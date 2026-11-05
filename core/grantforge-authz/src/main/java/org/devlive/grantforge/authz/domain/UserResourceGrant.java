// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A resource directly allowed to one account in its tenant. */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_user_resource_grant")
public class UserResourceGrant extends TenantScopedEntity implements ResourceGrant
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;
    @Column(name = "resource_id", nullable = false, updatable = false)
    private long resourceId;
    @Column(name = "expires_at")
    private @Nullable Instant expiresAt;
    @Column(name = "granted_by", nullable = false)
    private long grantedBy;

    /** For JPA. */
    protected UserResourceGrant() {}

    /**
     * Creates a direct grant.
     *
     * @param accountId the account
     * @param resource the resource
     * @param expiresAt optional expiry
     * @param actorId the grantor
     * @return the grant
     * @throws IllegalArgumentException if the resource cannot be granted
     */
    public static UserResourceGrant create(long accountId, Resource resource, @Nullable Instant expiresAt, long actorId)
    {
        if (!RoleGrant.GRANTABLE.contains(resource.getType())) {
            throw new IllegalArgumentException("resource type cannot be granted");
        }
        UserResourceGrant grant = new UserResourceGrant();
        grant.accountId = accountId;
        grant.resourceId = resource.requireId();
        grant.update(expiresAt, actorId);
        return grant;
    }

    /**
     * Updates the expiry and actor.
     *
     * @param expiry optional expiry
     * @param actorId the grantor
     */
    public void update(@Nullable Instant expiry, long actorId)
    {
        expiresAt = expiry;
        grantedBy = actorId;
    }
    /**
     * Returns the account receiving this grant.
     *
     * @return account ID
     */
    public long getAccountId()
    {
        return accountId;
    }
    @Override
    public long getResourceId()
    {
        return resourceId;
    }
    @Override
    public GrantEffect getEffect()
    {
        return GrantEffect.ALLOW;
    }
    /**
     * Returns when the grant expires.
     *
     * @return expiry, or null
     */
    public @Nullable Instant getExpiresAt()
    {
        return expiresAt;
    }
    @Override
    public boolean appliesAt(Instant now)
    {
        return expiresAt == null || now.isBefore(expiresAt);
    }
}
