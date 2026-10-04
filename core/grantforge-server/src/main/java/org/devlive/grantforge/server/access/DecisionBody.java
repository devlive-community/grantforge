// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * An approver's decision.
 *
 * @param days for how many days to grant, at most what was asked for; left out for that
 * @param comment what the approver says
 */
public record DecisionBody(@Nullable Integer days, @Size(max = 500) @Nullable String comment)
{
}
