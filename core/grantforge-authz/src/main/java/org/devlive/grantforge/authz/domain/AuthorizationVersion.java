// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * The counter of one scope (the shared catalog, or one tenant), raised by every change that permissions of the
 * scope are worked out from.
 */
@Entity
@Table(name = "gf_authz_version")
public class AuthorizationVersion
{
    @Id
    @Column(name = "scope", nullable = false, updatable = false, length = 64)
    private String scope = "";

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    /** For JPA. */
    protected AuthorizationVersion()
    {
    }

    /**
     * Creates the counter of a scope at its first change.
     *
     * @param scope the scope
     * @param now the time of the change
     * @return the counter, at 1
     */
    public static AuthorizationVersion first(String scope, Instant now)
    {
        AuthorizationVersion counter = new AuthorizationVersion();
        counter.scope = requireNonNull(scope, "scope");
        counter.version = 1;
        counter.updatedAt = requireNonNull(now, "now");
        return counter;
    }

    /**
     * Returns the scope.
     *
     * @return the scope
     */
    public String getScope()
    {
        return scope;
    }

    /**
     * Returns the counter.
     *
     * @return how many changes the scope has seen
     */
    public long getVersion()
    {
        return version;
    }
}
