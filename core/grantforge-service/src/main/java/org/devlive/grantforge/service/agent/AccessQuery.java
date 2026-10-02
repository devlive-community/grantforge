// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.service.domain.AccessOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Filters for reading access events; {@code null} means no filter.
 *
 * @param user text the user name contains, ignoring case
 * @param resource text the resource contains, ignoring case
 * @param accessType the access type
 * @param outcome allowed or denied
 * @param from the earliest moment, inclusive
 * @param until the latest moment, exclusive
 */
public record AccessQuery(@Nullable String user, @Nullable String resource, @Nullable String accessType, @Nullable AccessOutcome outcome,
        @Nullable Instant from, @Nullable Instant until)
{
    /** No filter. */
    public static final AccessQuery ALL = new AccessQuery(null, null, null, null, null, null);
}
