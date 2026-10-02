// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A period a policy applies in.
 *
 * @param from when it starts, or {@code null} for always
 * @param until when it ends (exclusive), or {@code null} for never
 */
public record ValidityPeriod(@Nullable Instant from, @Nullable Instant until)
{
}
