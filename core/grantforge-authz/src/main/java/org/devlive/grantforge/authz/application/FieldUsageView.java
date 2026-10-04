// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.authz.domain.FieldUsage;

import static java.util.Objects.requireNonNull;

/**
 * An API a secured field appears in.
 *
 * @param httpMethod the API's HTTP method
 * @param pathPattern the API's path pattern
 * @param direction whether the API returns or accepts the field
 */
public record FieldUsageView(String httpMethod, String pathPattern, FieldDirection direction)
{
    /** Checks that every value is present. */
    public FieldUsageView
    {
        requireNonNull(httpMethod, "httpMethod");
        requireNonNull(pathPattern, "pathPattern");
        requireNonNull(direction, "direction");
    }

    /**
     * Converts a usage.
     *
     * @param usage the usage
     * @return the view
     */
    public static FieldUsageView from(FieldUsage usage)
    {
        return new FieldUsageView(usage.getHttpMethod(), usage.getPathPattern(), usage.getDirection());
    }
}
