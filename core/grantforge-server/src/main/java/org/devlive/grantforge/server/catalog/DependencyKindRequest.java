// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.NotNull;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.jspecify.annotations.Nullable;

/**
 * The new kind of a dependency.
 *
 * @param kind required or optional
 */
public record DependencyKindRequest(@NotNull @Nullable DependencyKind kind)
{
}
