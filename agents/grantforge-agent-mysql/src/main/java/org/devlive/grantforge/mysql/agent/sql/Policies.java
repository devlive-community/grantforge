// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

/** The policies one statement is checked against, all from one snapshot. */
public interface Policies
{
    /**
     * Returns the policies of a snapshot; without one, every access is refused.
     *
     * @param snapshot the agent's snapshot, or {@code null} before it has one
     * @return the policies
     */
    static Policies of(@Nullable Snapshot snapshot)
    {
        return new Policies()
        {
            @Override
            public boolean enforced()
            {
                return snapshot == null || snapshot.serviceEnabled();
            }

            @Override
            public long version()
            {
                return snapshot == null ? -1 : snapshot.policyVersion();
            }

            @Override
            public AgentDecision decide(AccessRequest request)
            {
                return snapshot == null ? AgentDecision.withoutSnapshot() : snapshot.decide(request);
            }

            @Override
            public @Nullable DataMask mask(AccessRequest request)
            {
                return snapshot == null ? null : snapshot.mask(request);
            }

            @Override
            public @Nullable RowFilter rowFilter(AccessRequest request)
            {
                return snapshot == null ? null : snapshot.rowFilter(request);
            }
        };
    }

    /**
     * Returns whether the policies are enforced; a service that is not in use leaves MySQL's own grants alone.
     *
     * @return {@code true} if they are
     */
    boolean enforced();

    /**
     * Returns the version of the policies, which changes with them.
     *
     * @return the snapshot's policy version, or -1 without a snapshot
     */
    long version();

    /**
     * Decides an access.
     *
     * @param request the request
     * @return the decision
     */
    AgentDecision decide(AccessRequest request);

    /**
     * Finds how a column is masked.
     *
     * @param request the user and the column
     * @return the mask, or {@code null}
     */
    @Nullable DataMask mask(AccessRequest request);

    /**
     * Finds the rows of a table a user sees.
     *
     * @param request the user and the table
     * @return the filter, or {@code null}
     */
    @Nullable RowFilter rowFilter(AccessRequest request);
}
