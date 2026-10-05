// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

/**
 * What the agent decided about a request, with the policy and the policy version behind it, as access events report
 * them. Immutable.
 */
public final class AgentDecision
{
    /** How a request was decided. */
    public enum Outcome
    {
        /** A policy allowed it. */
        ALLOWED,
        /** A policy denied it. */
        DENIED,
        /**
         * No policy decided, the service is not in use or the agent has no snapshot yet; the system's own checks apply,
         * or access is denied where the agent is configured so.
         */
        NOT_DETERMINED
    }

    private static final AgentDecision WITHOUT_SNAPSHOT = new AgentDecision(Outcome.NOT_DETERMINED, null, null);

    private final Outcome outcome;
    private final @Nullable Long policyId;
    private final @Nullable Long policyVersion;

    private AgentDecision(Outcome outcome, @Nullable Long policyId, @Nullable Long policyVersion)
    {
        this.outcome = outcome;
        this.policyId = policyId;
        this.policyVersion = policyVersion;
    }

    static AgentDecision allowed(long policyVersion, long policyId)
    {
        return new AgentDecision(Outcome.ALLOWED, policyId, policyVersion);
    }

    static AgentDecision denied(long policyVersion, long policyId)
    {
        return new AgentDecision(Outcome.DENIED, policyId, policyVersion);
    }

    static AgentDecision notDetermined(long policyVersion)
    {
        return new AgentDecision(Outcome.NOT_DETERMINED, null, policyVersion);
    }

    /**
     * Returns an undetermined decision before an agent has an applied snapshot.
     *
     * @return the decision, without a policy id or version
     */
    public static AgentDecision withoutSnapshot()
    {
        return WITHOUT_SNAPSHOT;
    }

    /**
     * Returns how the request was decided.
     *
     * @return the outcome
     */
    public Outcome outcome()
    {
        return outcome;
    }

    /**
     * Returns whether a policy allowed the request.
     *
     * @return {@code true} if one did
     */
    public boolean allowed()
    {
        return outcome == Outcome.ALLOWED;
    }

    /**
     * Returns whether GrantForge decided, rather than leaving it to the system.
     *
     * @return {@code true} if a policy allowed or denied the request
     */
    public boolean determined()
    {
        return outcome != Outcome.NOT_DETERMINED;
    }

    /**
     * Returns the policy that decided.
     *
     * @return its id, or {@code null} if none did
     */
    public @Nullable Long policyId()
    {
        return policyId;
    }

    /**
     * Returns the policy version the decision was made with.
     *
     * @return the version, or {@code null} without a snapshot
     */
    public @Nullable Long policyVersion()
    {
        return policyVersion;
    }

    @Override
    public String toString()
    {
        return outcome + (policyId == null ? "" : " by policy " + policyId) + (policyVersion == null ? "" : " at version " + policyVersion);
    }
}
