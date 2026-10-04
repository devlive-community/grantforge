// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * How many items of a round have a decision and outcome.
 *
 * @param roundId the round
 * @param decision the decision
 * @param outcome the outcome, or {@code null} before the round completed
 * @param count how many items
 */
public record ReviewTally(long roundId, ReviewDecision decision, @Nullable ReviewOutcome outcome, long count)
{
    /** Validates the values. */
    public ReviewTally
    {
        requireNonNull(decision, "decision");
    }
}
