// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.FieldUsageView;
import org.devlive.grantforge.authz.domain.FieldDirection;

/**
 * An API a secured field appears in.
 *
 * @param httpMethod the API's HTTP method
 * @param pathPattern the API's path pattern
 * @param direction {@code READ} if the API returns the field, {@code WRITE} if it accepts it
 */
public record FieldUsageResponse(String httpMethod, String pathPattern, FieldDirection direction)
{
    /**
     * Converts a usage.
     *
     * @param usage the usage
     * @return the response
     */
    public static FieldUsageResponse from(FieldUsageView usage)
    {
        return new FieldUsageResponse(usage.httpMethod(), usage.pathPattern(), usage.direction());
    }
}
