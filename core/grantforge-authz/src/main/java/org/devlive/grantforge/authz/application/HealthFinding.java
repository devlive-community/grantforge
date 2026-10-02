// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * One inconsistency of the catalog.
 *
 * @param issue what is wrong
 * @param resourceId the resource concerned: the granted one, the button, the API or the dependent
 * @param resourceCode its code
 * @param relatedId for a dependency, the resource depended on
 * @param relatedCode its code
 * @param tenantCode for a grant, the tenant whose role holds it
 * @param roleCode for a grant, the role holding it
 */
public record HealthFinding(HealthIssue issue, long resourceId, String resourceCode, @Nullable Long relatedId,
        @Nullable String relatedCode, @Nullable String tenantCode, @Nullable String roleCode)
{
    /** Checks that the issue and code are present. */
    public HealthFinding
    {
        requireNonNull(issue, "issue");
        requireNonNull(resourceCode, "resourceCode");
    }
}
