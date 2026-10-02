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
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * An HTTP endpoint the server serves (or once served), found by scanning the controllers at start-up. Endpoints
 * that need a permission point at the API resource of that permission. Changes since the last review are marked
 * so administrators see which routes appeared, changed their access or went away.
 */
@Entity
@Table(name = "gf_api_endpoint")
public class ApiEndpoint
        extends BaseEntity
{
    @Column(name = "http_method", nullable = false, length = 8, updatable = false)
    private String httpMethod = "";

    @Column(name = "path_pattern", nullable = false, length = 255, updatable = false)
    private String pathPattern = "";

    @Column(name = "handler", nullable = false, length = 255)
    private String handler = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "access_level", nullable = false, length = 16)
    private EndpointAccess access = EndpointAccess.PERMISSION;

    @Column(name = "permission", length = 128)
    private @Nullable String permission;

    @Column(name = "resource_id")
    private @Nullable Long resourceId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "change_kind", length = 16)
    private @Nullable EndpointChange change;

    @Column(name = "changed_at")
    private @Nullable Instant changedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.EPOCH;

    /** For JPA. */
    protected ApiEndpoint()
    {
    }

    /**
     * Records an endpoint seen for the first time, marked as {@link EndpointChange#ADDED}.
     *
     * @param httpMethod the method, such as {@code GET}
     * @param pathPattern the path pattern, such as {@code /api/v1/users/{id}}
     * @param declaration what the code declares
     * @param resourceId the API resource of its permission, or {@code null} if it needs none
     * @param now the current time
     * @return the endpoint
     */
    public static ApiEndpoint discover(String httpMethod, String pathPattern, Declaration declaration,
            @Nullable Long resourceId, Instant now)
    {
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.httpMethod = Strings.requireNonBlank(httpMethod, "httpMethod").toUpperCase(Locale.ROOT);
        endpoint.pathPattern = Strings.requireNonBlank(pathPattern, "pathPattern");
        endpoint.apply(declaration, resourceId);
        endpoint.mark(EndpointChange.ADDED, now);
        endpoint.lastSeenAt = now;
        return endpoint;
    }

    /**
     * Records that the endpoint is still served, as the code now declares it. A different access or permission, or
     * a return after removal, marks it {@link EndpointChange#CHANGED} unless an unreviewed addition is pending.
     *
     * @param declaration what the code declares
     * @param resourceId the API resource of its permission, or {@code null}
     * @param now the current time
     * @return {@code true} if it was marked as changed
     */
    public boolean seen(Declaration declaration, @Nullable Long resourceId, Instant now)
    {
        boolean changed = !active || access != declaration.access() || !Objects.equals(permission, declaration.permission());
        apply(declaration, resourceId);
        active = true;
        lastSeenAt = requireNonNull(now, "now");
        if (changed && change != EndpointChange.ADDED) {
            mark(EndpointChange.CHANGED, now);
            return true;
        }
        return false;
    }

    /**
     * Records that the server no longer serves the endpoint.
     *
     * @param now the current time
     * @return {@code true} if it was active until now
     */
    public boolean vanish(Instant now)
    {
        if (!active) {
            return false;
        }
        active = false;
        mark(EndpointChange.REMOVED, now);
        return true;
    }

    /** Clears the change mark after an administrator reviewed it. */
    // NULL is how the nullable change columns say "nothing to review".
    @SuppressWarnings("PMD.NullAssignment")
    public void reviewed()
    {
        change = null;
        changedAt = null;
    }

    private void apply(Declaration declaration, @Nullable Long resource)
    {
        requireNonNull(declaration, "declaration");
        handler = declaration.handler();
        access = declaration.access();
        permission = declaration.permission();
        resourceId = resource;
    }

    private void mark(EndpointChange kind, Instant now)
    {
        change = kind;
        changedAt = requireNonNull(now, "now");
    }

    /**
     * Returns the HTTP method.
     *
     * @return the uppercase method
     */
    public String getHttpMethod()
    {
        return httpMethod;
    }

    /**
     * Returns the path pattern.
     *
     * @return the pattern
     */
    public String getPathPattern()
    {
        return pathPattern;
    }

    /**
     * Returns the handling method.
     *
     * @return {@code Controller#method}
     */
    public String getHandler()
    {
        return handler;
    }

    /**
     * Returns who may call the endpoint.
     *
     * @return the access
     */
    public EndpointAccess getAccess()
    {
        return access;
    }

    /**
     * Returns the permission callers need.
     *
     * @return the permission code, or {@code null} for public and authenticated endpoints
     */
    public @Nullable String getPermission()
    {
        return permission;
    }

    /**
     * Returns the API resource of the permission.
     *
     * @return the resource ID, or {@code null}
     */
    public @Nullable Long getResourceId()
    {
        return resourceId;
    }

    /**
     * Returns whether the server serves the endpoint.
     *
     * @return {@code false} once it disappeared
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * Returns the unreviewed change.
     *
     * @return the change, or {@code null}
     */
    public @Nullable EndpointChange getChange()
    {
        return change;
    }

    /**
     * Returns when the unreviewed change happened.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getChangedAt()
    {
        return changedAt;
    }

    /**
     * Returns when the server last served the endpoint.
     *
     * @return the time of the last start-up that found it
     */
    public Instant getLastSeenAt()
    {
        return lastSeenAt;
    }

    /**
     * What the code declares about an endpoint.
     *
     * @param handler the handling method, {@code Controller#method}
     * @param access who may call it
     * @param permission the permission for {@link EndpointAccess#PERMISSION}, else {@code null}
     */
    public record Declaration(String handler, EndpointAccess access, @Nullable String permission)
    {
        /**
         * Checks that a permission is given exactly when the access needs one.
         *
         * @throws IllegalArgumentException otherwise
         */
        public Declaration
        {
            requireNonNull(handler, "handler");
            requireNonNull(access, "access");
            if (access == EndpointAccess.PERMISSION == (permission == null)) {
                throw new IllegalArgumentException(handler + ": a permission is required exactly for PERMISSION access");
            }
        }
    }
}
