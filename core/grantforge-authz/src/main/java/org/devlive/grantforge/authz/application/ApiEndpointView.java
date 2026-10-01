// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.authz.domain.EndpointChange;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An endpoint of the API catalog.
 *
 * @param id the endpoint ID
 * @param httpMethod the method
 * @param pathPattern the path pattern
 * @param handler the handling method
 * @param access who may call it
 * @param permission the permission it needs, or {@code null}
 * @param resourceId the API resource of the permission, or {@code null}
 * @param active whether the server serves it
 * @param change the unreviewed change, or {@code null}
 * @param changedAt when the unreviewed change happened, or {@code null}
 * @param lastSeenAt when the server last served it
 */
public record ApiEndpointView(long id, String httpMethod, String pathPattern, String handler, EndpointAccess access,
        @Nullable String permission, @Nullable Long resourceId, boolean active, @Nullable EndpointChange change,
        @Nullable Instant changedAt, Instant lastSeenAt)
{
    /** Validates the values. */
    public ApiEndpointView
    {
        requireNonNull(httpMethod, "httpMethod");
        requireNonNull(pathPattern, "pathPattern");
        requireNonNull(handler, "handler");
        requireNonNull(access, "access");
        requireNonNull(lastSeenAt, "lastSeenAt");
    }

    /**
     * Converts an endpoint.
     *
     * @param endpoint the endpoint
     * @return the view
     */
    public static ApiEndpointView from(ApiEndpoint endpoint)
    {
        return new ApiEndpointView(endpoint.requireId(), endpoint.getHttpMethod(), endpoint.getPathPattern(),
                endpoint.getHandler(), endpoint.getAccess(), endpoint.getPermission(), endpoint.getResourceId(),
                endpoint.isActive(), endpoint.getChange(), endpoint.getChangedAt(), endpoint.getLastSeenAt());
    }
}
