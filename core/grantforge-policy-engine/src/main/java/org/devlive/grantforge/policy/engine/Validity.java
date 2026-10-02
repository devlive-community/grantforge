// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A period a policy applies in: from {@code from} (inclusive) until {@code until} (exclusive), either end open. */
public final class Validity
{
    private final @Nullable Instant from;
    private final @Nullable Instant until;

    private Validity(@Nullable Instant from, @Nullable Instant until)
    {
        if (from != null && until != null && !until.isAfter(from)) {
            throw new IllegalArgumentException("a validity period must end after it starts");
        }
        this.from = from;
        this.until = until;
    }

    /**
     * Describes a period.
     *
     * @param from when it starts, or {@code null} for always
     * @param until when it ends (exclusive), or {@code null} for never
     * @return the period
     */
    public static Validity between(@Nullable Instant from, @Nullable Instant until)
    {
        return new Validity(from, until);
    }

    /**
     * Returns whether the period contains a moment.
     *
     * @param moment the moment
     * @return {@code true} if it lies within
     */
    public boolean contains(Instant moment)
    {
        return (from == null || !moment.isBefore(from)) && (until == null || moment.isBefore(until));
    }
}
