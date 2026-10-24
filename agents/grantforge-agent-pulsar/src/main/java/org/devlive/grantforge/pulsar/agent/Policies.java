// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

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
 * The policies one check is decided against, all from one snapshot. As Pulsar's superusers may do everything, a check is
 * granted to the administrators of the broker's cluster first. Otherwise, as with Pulsar's own grants, a permission on a
 * tenant or a namespace covers what is in it, so a check is decided at each scope of its resource, from the tenant down: a
 * denial at any of them refuses it, otherwise an allowance at any of them grants it. The client's address goes with every
 * request, for the conditions that look at it.
 */
final class Policies
{
    /** The cluster's administration, which grants everything in the cluster. */
    static final String ADMIN = "admin";

    private static final Logger LOG = LoggerFactory.getLogger(Policies.class);

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
     * Returns whether the policies are enforced; a service that is not in use leaves Pulsar unchecked, as it is with
     * {@code authorizationEnabled} off.
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
    Verdict decide(Caller caller, PulsarResource resource, String... accessTypes)
    {
        String cluster = resource.cluster();
        if (cluster != null && accessTypes.length > 0) {
            Verdict administrator = decideOne(caller, PulsarResource.cluster(cluster), ADMIN);
            if (administrator.allowed()) {
                return administrator;
            }
        }
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

    private Verdict decideOne(Caller caller, PulsarResource resource, String accessType)
    {
        Snapshot current = snapshot;
        if (current == null) {
            return new Verdict(accessType, AgentDecision.withoutSnapshot());
        }
        AgentDecision allowed = null;
        AgentDecision last = AgentDecision.withoutSnapshot();
        for (Map<String, String> scope : resource.scopes()) {
            AgentDecision decision = decision(current, caller, accessType, scope);
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

    private AgentDecision decision(Snapshot current, Caller caller, String accessType, Map<String, String> scope)
    {
        List<String> key = new ArrayList<>();
        key.add(caller.user());
        key.add(String.join(",", caller.groups()));
        key.add(String.valueOf(caller.clientIp()));
        key.add(accessType);
        key.addAll(scope.keySet());
        key.addAll(scope.values());
        return decisions.decide(current, key, ignored -> {
            AccessRequest.Builder request = AccessRequest.builder(caller.user(), accessType).groups(caller.groups().toArray(new String[0]));
            for (Map.Entry<String, String> level : scope.entrySet()) {
                request.resource(level.getKey(), level.getValue());
            }
            String clientIp = caller.clientIp();
            if (clientIp != null) {
                request.context(Collections.<String, Object>singletonMap(AddressRange.CLIENT_ADDRESS, clientIp));
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
