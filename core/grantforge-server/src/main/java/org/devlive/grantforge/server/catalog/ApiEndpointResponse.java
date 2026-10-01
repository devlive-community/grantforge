// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiEndpointView;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.authz.domain.EndpointChange;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An endpoint of the API catalog; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the endpoint ID
 * @param httpMethod the method, such as {@code GET}
 * @param pathPattern the path pattern
 * @param handler the handling method, {@code Controller#method}
 * @param access who may call it
 * @param permission the permission it needs, or {@code null}
 * @param resourceId the API resource of the permission, or {@code null}
 * @param active whether the server serves it
 * @param change the change since the last review, or {@code null}
 * @param changedAt when that change happened, or {@code null}
 * @param lastSeenAt when the server last served it
 */
public record ApiEndpointResponse(String id, String httpMethod, String pathPattern, String handler, EndpointAccess access,
        @Nullable String permission, @Nullable String resourceId, boolean active, @Nullable EndpointChange change,
        @Nullable Instant changedAt, Instant lastSeenAt)
{
    /**
     * Converts a view.
     *
     * @param endpoint the view
     * @return the response
     */
    public static ApiEndpointResponse from(ApiEndpointView endpoint)
    {
        Long resource = endpoint.resourceId();
        return new ApiEndpointResponse(Long.toString(endpoint.id()), endpoint.httpMethod(), endpoint.pathPattern(),
                endpoint.handler(), endpoint.access(), endpoint.permission(), resource == null ? null : Long.toString(resource),
                endpoint.active(), endpoint.change(), endpoint.changedAt(), endpoint.lastSeenAt());
    }
}
