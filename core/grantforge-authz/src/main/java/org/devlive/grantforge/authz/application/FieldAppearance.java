// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.persistence.secured.DeclaredField;

import static java.util.Objects.requireNonNull;

/**
 * That an API returns or accepts a secured field, as the code declares it.
 *
 * @param field the field
 * @param httpMethod the API's HTTP method
 * @param pathPattern the API's path pattern
 * @param direction whether the API returns or accepts the field
 */
public record FieldAppearance(DeclaredField field, String httpMethod, String pathPattern, FieldDirection direction)
{
    /** Checks that every value is present. */
    public FieldAppearance
    {
        requireNonNull(field, "field");
        requireNonNull(httpMethod, "httpMethod");
        requireNonNull(pathPattern, "pathPattern");
        requireNonNull(direction, "direction");
    }
}
