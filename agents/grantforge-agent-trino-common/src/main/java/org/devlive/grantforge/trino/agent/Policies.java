// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The policies one check is decided against, all from one snapshot. A permission on a catalog, schema or table covers
 * what is within it, so such a resource is decided at each scope that names it, from its catalog down: a denial at any
 * of them refuses, otherwise an allowance at any of them grants. A user who may use something within a resource, such
 * as one table of a schema, may see the resource listed; see {@link #uses}.
 */
final class Policies
{
    private static final Logger LOG = Logger.getLogger(Policies.class.getName());

    private final @Nullable Snapshot snapshot;
    private final DecisionCache decisions;

    /**
     * Creates the policies of a snapshot.
     *
     * @param snapshot the snapshot, or {@code null} to refuse everything
     * @param decisions the decisions kept
     */
    Policies(@Nullable Snapshot snapshot, DecisionCache decisions)
    {
        this.snapshot = snapshot;
        this.decisions = decisions;
    }

    /**
     * Returns whether the policies are enforced; a service that is not in use leaves Trino unchecked, as it is without
     * an access control.
     *
     * @return {@code true} if they are
     */
    boolean enforced()
    {
        return snapshot == null || snapshot.serviceEnabled();
    }

    /**
     * Returns whether there are policies at all, rather than none received yet.
     *
     * @return {@code true} if there is a snapshot
     */
    boolean received()
    {
        return snapshot != null;
    }

    /**
     * Decides whether a caller may do one of some access types to a resource: the first access type allowed grants it.
     *
     * @param caller who asks
     * @param resource the resource
     * @param accessTypes the access types, any of which suffices
     * @return the verdict
     */
    Verdict decide(Caller caller, TrinoResource resource, String... accessTypes)
    {
        Verdict refused = null;
        for (String accessType : accessTypes) {
            Verdict verdict = decideOne(caller, resource, accessType);
            if (verdict.allowed()) {
                return verdict;
            }
            if (refused == null || (verdict.denied() && !refused.denied())) {
                refused = verdict;
            }
        }
        if (refused == null) {
            throw new IllegalArgumentException("no access type to decide");
        }
        return refused;
    }

    private Verdict decideOne(Caller caller, TrinoResource resource, String accessType)
    {
        Snapshot current = snapshot;
        if (current == null) {
            return new Verdict(accessType, AgentDecision.withoutSnapshot());
        }
        AgentDecision allowed = null;
        AgentDecision last = AgentDecision.withoutSnapshot();
        for (Map<String, String> scope : resource.scopes()) {
            AgentDecision decision = decision(current, caller, accessType, scope, false);
            if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                return new Verdict(accessType, decision);
            }
            if (decision.allowed()) {
                allowed = decision;
            }
            last = decision;
        }
        return new Verdict(accessType, allowed == null ? last : allowed);
    }

    /**
     * Returns whether a caller may use a resource or something within it with one of some access types, as to see it
     * listed. A denial of the resource itself, or of a scope above it, hides it.
     *
     * @param caller who asks
     * @param resource the resource
     * @param accessTypes the access types
     * @return {@code true} if any is allowed on it or within it
     */
    boolean uses(Caller caller, TrinoResource resource, String... accessTypes)
    {
        Snapshot current = snapshot;
        if (current == null) {
            return false;
        }
        Map<String, String> whole = resource.path();
        for (String accessType : accessTypes) {
            Verdict verdict = decideOne(caller, resource, accessType);
            if (verdict.allowed()) {
                return true;
            }
            if (!verdict.denied() && decision(current, caller, accessType, whole, true).allowed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the rows of a table a caller sees.
     *
     * @param caller who reads
     * @param table the table
     * @return the filter, or {@code null} when none applies
     */
    @Nullable RowFilter rowFilter(Caller caller, TrinoResource table)
    {
        Snapshot current = snapshot;
        return current == null ? null : current.rowFilter(request(caller, TrinoAccess.SELECT, table.path(), false));
    }

    /**
     * Finds how a column is masked for a caller.
     *
     * @param caller who reads
     * @param column the column
     * @return the mask, or {@code null} when none applies
     */
    @Nullable DataMask mask(Caller caller, TrinoResource column)
    {
        Snapshot current = snapshot;
        return current == null ? null : current.mask(request(caller, TrinoAccess.SELECT, column.path(), false));
    }

    private AgentDecision decision(Snapshot current, Caller caller, String accessType, Map<String, String> scope, boolean below)
    {
        List<String> key = new ArrayList<>();
        key.add(below ? "below" : "at");
        key.add(caller.user());
        key.add(String.join(",", caller.groups()));
        key.add(accessType);
        key.addAll(scope.keySet());
        key.addAll(scope.values());
        return decisions.decide(current, key, ignored -> {
            try {
                return current.decide(request(caller, accessType, scope, below));
            }
            catch (RuntimeException broken) {
                LOG.log(Level.WARNING, "GrantForge cannot decide {0} on {1} for {2}: {3}",
                        new Object[] {accessType, scope, caller.user(), broken.getMessage()});
                return AgentDecision.withoutSnapshot();
            }
        });
    }

    private static AccessRequest request(Caller caller, String accessType, Map<String, String> scope, boolean below)
    {
        AccessRequest.Builder request = AccessRequest.builder(caller.user(), accessType).groups(caller.groups().toArray(new String[0])).below(below);
        for (Map.Entry<String, String> level : scope.entrySet()) {
            request.resource(level.getKey(), level.getValue());
        }
        return request.build();
    }

    /**
     * What was decided about one access type, with the decision that tells.
     *
     * @param accessType the access type decided
     * @param decision the decision that tells: the denial, the most specific allowance, or that nothing was decided
     */
    record Verdict(String accessType, AgentDecision decision)
    {
        /**
         * Returns whether access is allowed.
         *
         * @return {@code true} if it is
         */
        boolean allowed()
        {
            return decision.allowed();
        }

        /**
         * Returns whether a policy denied access, rather than none allowing it.
         *
         * @return {@code true} if one did
         */
        boolean denied()
        {
            return decision.outcome() == AgentDecision.Outcome.DENIED;
        }
    }
}
