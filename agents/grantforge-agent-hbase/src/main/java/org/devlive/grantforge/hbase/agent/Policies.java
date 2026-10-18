// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The policies one request is checked against, all from one snapshot. HBase's permissions cover everything within their
 * scope, so a request is decided at each scope it names, from its namespace down: a denial at any of them refuses it,
 * otherwise an allowance at any of them grants it. A user who may use something within a table, such as one of its
 * column families, may see the table; see {@link #uses}.
 */
final class Policies
{
    private static final Logger LOG = LoggerFactory.getLogger(Policies.class);

    private final @Nullable Snapshot snapshot;
    private final DecisionCache decisions;
    private final boolean namesColumns;

    /**
     * Creates the policies of a snapshot.
     *
     * @param snapshot the snapshot, or {@code null} to refuse everything
     * @param decisions the decisions kept
     * @param namesColumns whether the snapshot's policies name column families or columns
     */
    Policies(@Nullable Snapshot snapshot, DecisionCache decisions, boolean namesColumns)
    {
        this.snapshot = snapshot;
        this.decisions = decisions;
        this.namesColumns = namesColumns;
    }

    /**
     * Returns whether the policies are enforced; a service that is not in use leaves HBase unchecked, as it is without an
     * authorizer.
     *
     * @return {@code true} if they are
     */
    boolean enforced()
    {
        return snapshot == null || snapshot.serviceEnabled();
    }

    /**
     * Returns whether policies name column families or columns, so that a user allowed a table may still be refused
     * some of its columns.
     *
     * @return {@code true} if they do
     */
    boolean namesColumns()
    {
        return namesColumns;
    }

    /**
     * Decides whether a caller may do one of some access types to a resource: the first access type allowed grants it.
     *
     * @param caller who asks
     * @param resource the resource
     * @param accessTypes the access types, any of which suffices
     * @return the verdict
     */
    Verdict decide(Caller caller, HBaseResource resource, String... accessTypes)
    {
        Verdict refused = null;
        for (String accessType : accessTypes) {
            Verdict verdict = decideOne(caller, resource, accessType);
            if (verdict.allowed()) {
                return verdict;
            }
            if (refused == null || verdict.denied() && !refused.denied()) {
                refused = verdict;
            }
        }
        if (refused == null) {
            throw new IllegalArgumentException("no access type to decide");
        }
        return refused;
    }

    private Verdict decideOne(Caller caller, HBaseResource resource, String accessType)
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
     * listed or to read the columns of a table it may read some of. A denial of the resource itself, or of a scope above
     * it, hides it.
     *
     * @param caller who asks
     * @param resource the resource
     * @param accessTypes the access types
     * @return {@code true} if any is allowed on it or within it
     */
    boolean uses(Caller caller, HBaseResource resource, String... accessTypes)
    {
        Snapshot current = snapshot;
        if (current == null) {
            return false;
        }
        List<Map<String, String>> scopes = resource.scopes();
        Map<String, String> deepest = scopes.get(scopes.size() - 1);
        for (String accessType : accessTypes) {
            Verdict whole = decideOne(caller, resource, accessType);
            if (whole.allowed()) {
                return true;
            }
            if (!whole.denied() && decision(current, caller, accessType, deepest, true).allowed()) {
                return true;
            }
        }
        return false;
    }

    private AgentDecision decision(Snapshot current, Caller caller, String accessType, Map<String, String> scope, boolean below)
    {
        List<String> key = new ArrayList<>();
        key.add(below ? "below" : "at");
        key.add(caller.user());
        key.add(String.join(",", caller.groups()));
        key.add(String.valueOf(caller.clientIp()));
        key.add(accessType);
        key.addAll(scope.keySet());
        key.addAll(scope.values());
        return decisions.decide(current, key, ignored -> {
            AccessRequest.Builder request = AccessRequest.builder(caller.user(), accessType).groups(caller.groups().toArray(new String[0]))
                    .below(below);
            for (Map.Entry<String, String> level : scope.entrySet()) {
                request.resource(level.getKey(), level.getValue());
            }
            String clientIp = caller.clientIp();
            if (clientIp != null) {
                request.context(Collections.<String, Object>singletonMap("clientAddress", clientIp));
            }
            try {
                return current.decide(request.build());
            }
            catch (RuntimeException broken) {
                LOG.warn("GrantForge cannot decide {} on {} for {}: {}", accessType, scope, caller.user(), broken.getMessage());
                return AgentDecision.withoutSnapshot();
            }
        });
    }

    /** What was decided about one access type, with the decision that tells. */
    static final class Verdict
    {
        private final String accessType;
        private final AgentDecision decision;

        Verdict(String accessType, AgentDecision decision)
        {
            this.accessType = accessType;
            this.decision = decision;
        }

        /**
         * Returns the access type decided.
         *
         * @return it
         */
        String accessType()
        {
            return accessType;
        }

        /**
         * Returns the decision that tells: the denial, the most specific allowance, or that nothing was decided.
         *
         * @return it
         */
        AgentDecision decision()
        {
            return decision;
        }

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
