// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

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

            @Override
            public Set<String> rolesOf(String user)
            {
                return snapshot == null ? Collections.<String>emptySet() : snapshot.rolesOf(user);
            }
        };
    }

    /**
     * Returns whether the policies are enforced; a service that is not in use leaves Hive unchecked, as it is
     * without an authorizer.
     *
     * @return {@code true} if they are
     */
    boolean enforced();

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

    /**
     * Returns the roles the snapshot gives a user.
     *
     * @param user the user
     * @return the roles
     */
    Set<String> rolesOf(String user);
}
