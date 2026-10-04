// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

/**
 * A request for a new client secret.
 *
 * @param graceHours how long the current secret keeps working, up to a week; 0 when absent
 */
public record ClientRotateRequest(@Min(0) @Max(168) @Nullable Integer graceHours)
{
}
