// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.jspecify.annotations.Nullable;

/**
 * A new dependency of a resource.
 *
 * @param dependsOnId the resource it needs, of the same application
 * @param kind how strongly; {@code REQUIRED} if omitted
 */
public record DependencyRequest(@NotBlank @Size(max = 20) @Nullable String dependsOnId, @Nullable DependencyKind kind)
{
}
