// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

/**
 * The answer to an access request, with the policy that gave it. Without a policy that decides, access is denied
 * and the decision says so ({@link Outcome#NOT_DETERMINED}), so an agent may fall back, for example to HDFS's own
 * permissions.
 */
public final class Decision
{
    private static final Decision NOT_DETERMINED = new Decision(Outcome.NOT_DETERMINED, null);

    private final Outcome outcome;
    private final @Nullable Long policyId;

    private Decision(Outcome outcome, @Nullable Long policyId)
    {
        this.outcome = outcome;
        this.policyId = policyId;
    }

    static Decision allowedBy(long policyId)
    {
        return new Decision(Outcome.ALLOWED, policyId);
    }

    static Decision deniedBy(long policyId)
    {
        return new Decision(Outcome.DENIED, policyId);
    }

    static Decision notDetermined()
    {
        return NOT_DETERMINED;
    }

    /**
     * Returns the outcome.
     *
     * @return allowed, denied, or not determined by any policy
     */
    public Outcome outcome()
    {
        return outcome;
    }

    /**
     * Returns whether access is allowed.
     *
     * @return {@code true} only if a policy allows it
     */
    public boolean allowed()
    {
        return outcome == Outcome.ALLOWED;
    }

    /**
     * Returns the policy that decided.
     *
     * @return its id, or {@code null} if no policy decided
     */
    public @Nullable Long policyId()
    {
        return policyId;
    }

    @Override
    public boolean equals(@Nullable Object other)
    {
        if (!(other instanceof Decision)) {
            return false;
        }
        Decision that = (Decision) other;
        return outcome == that.outcome && (policyId == null ? that.policyId == null : policyId.equals(that.policyId));
    }

    @Override
    public int hashCode()
    {
        return outcome.hashCode() * 31 + (policyId == null ? 0 : policyId.hashCode());
    }

    @Override
    public String toString()
    {
        return policyId == null ? outcome.name() : outcome.name() + " by policy " + policyId;
    }

    /** What a decision says. */
    public enum Outcome
    {
        /** A policy allows the access. */
        ALLOWED,

        /** A policy denies the access. */
        DENIED,

        /** No policy decides; access is denied. */
        NOT_DETERMINED
    }
}
