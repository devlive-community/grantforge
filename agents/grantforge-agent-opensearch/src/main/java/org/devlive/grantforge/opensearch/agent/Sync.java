// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * One sync of OpenSearch with the policies: reads what the cluster holds, plans each user's role from the snapshot at
 * the present moment, so that policies with validity periods start and end on time, applies the plans and warns of
 * roles outside GrantForge that give what the policies deny. Row filters are queries OpenSearch checks before they are
 * granted. Without a snapshot nothing changes; a snapshot of a service that is not in use, or of another service type,
 * grants nothing.
 */
final class Sync
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "opensearch";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final OpenSearch search;
    private final AgentConfig config;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Clock clock;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<Inventory> inventory = new AtomicReference<>(Inventory.EMPTY);
    private boolean waiting;
    private String reportedState = "";
    private long version = Long.MIN_VALUE;

    Sync(OpenSearch search, AgentConfig config, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        this.search = search;
        this.config = config;
        this.snapshots = snapshots;
        this.clock = clock;
        this.reconciler = new Reconciler(search, config.rolePrefix());
    }

    /**
     * Brings OpenSearch in step with the policies once.
     *
     * @return what changed, or {@code null} without a snapshot
     * @throws OpenSearchFailure when the cluster fails a request or cannot be reached
     */
    Reconciler.@Nullable Outcome run()
    {
        running.lock();
        try {
            return runOnce();
        }
        finally {
            running.unlock();
        }
    }

    private Reconciler.@Nullable Outcome runOnce()
    {
        Snapshot snapshot = snapshots.get();
        if (snapshot == null) {
            if (!waiting) {
                LOG.info("The GrantForge OpenSearch agent waits for its first policy snapshot; OpenSearch is left as it is");
                waiting = true;
            }
            return null;
        }
        ServerState state = ServerState.read(search, config.rolePrefix(), config.unmanaged(), snapshot.users());
        Map<String, Set<String>> held = new HashMap<>();
        state.internalUsers().forEach((name, user) -> held.put(name, user.backendRoles()));
        groups.set(held);
        inventory.set(state.inventory());
        Instant now = clock.instant();
        Function<Account, Plan> plans = null;
        if (!SERVICE_TYPE.equals(snapshot.serviceType())) {
            report("The agent's token is bound to a " + snapshot.serviceType() + " service, not an OpenSearch one; GrantForge grants nothing");
        }
        else if (!snapshot.serviceEnabled()) {
            report("The OpenSearch service " + snapshot.service() + " is not in use; GrantForge grants nothing and OpenSearch's own roles apply");
        }
        else {
            reportedState = "";
            Map<String, @Nullable String> checked = new HashMap<>();
            Planner planner = new Planner(snapshot, state.inventory(), now, query -> checked.computeIfAbsent(query, this::refusal));
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, plans);
        if (plans != null) {
            conflicts.check(state, snapshot, now);
        }
        if (outcome.changed() || version != snapshot.policyVersion()) {
            LOG.info("GrantForge policy version " + snapshot.policyVersion() + " applied to OpenSearch: " + outcome.users() + " users, "
                    + outcome.created() + " roles created, " + outcome.updated() + " updated, " + outcome.dropped() + " dropped, " + outcome.mappings()
                    + " role mappings changed" + (outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : ""));
            version = snapshot.policyVersion();
        }
        return outcome;
    }

    /**
     * Says why OpenSearch would not run a row filter's query, as its validate API checks it against the indices.
     *
     * @param query the query, as JSON
     * @return why not, or {@code null} when it would
     * @throws OpenSearchFailure when the cluster cannot be reached
     */
    @Nullable String refusal(String query)
    {
        JsonNode parsed;
        try {
            parsed = JSON.readTree(query);
        }
        catch (IOException broken) {
            return "it is no JSON";
        }
        if (parsed == null || !parsed.isObject() || parsed.size() != 1) {
            return "it is no JSON object of one query";
        }
        ObjectNode body = JSON.createObjectNode();
        body.set("query", parsed);
        try {
            JsonNode answer = search.send("POST", "/*,-.*/_validate/query", Map.of("explain", "true", "allow_no_indices", "true",
                    "ignore_unavailable", "true"), body);
            if (answer.path("valid").asBoolean(false)) {
                return null;
            }
            String error = answer.path("error").asText("");
            for (JsonNode explanation : answer.path("explanations")) {
                if (error.isEmpty()) {
                    error = explanation.path("error").asText("");
                }
            }
            return error.isEmpty() ? "OpenSearch finds it invalid" : error;
        }
        catch (OpenSearchFailure refused) {
            if (refused.unreachable()) {
                throw refused;
            }
            return refused.getMessage();
        }
    }

    private void report(String state)
    {
        if (!state.equals(reportedState)) {
            LOG.severe(state);
            reportedState = state;
        }
    }

    /**
     * Returns the backend roles an internal user had at the last sync, which policies name as groups.
     *
     * @param user the user
     * @return the backend roles, none for a user the security plugin does not hold
     */
    Set<String> groupsOf(String user)
    {
        Map<String, Set<String>> last = groups.get();
        return last == null ? Set.of() : last.getOrDefault(user, Set.of());
    }

    /**
     * Returns the indices and data streams the cluster had at the last sync.
     *
     * @return the inventory, empty before the first sync
     */
    Inventory inventory()
    {
        Inventory last = inventory.get();
        return last == null ? Inventory.EMPTY : last;
    }

    /**
     * Returns the role the agent keeps for a user.
     *
     * @param user the user
     * @return the role's name
     */
    String role(String user)
    {
        return reconciler.role(user);
    }
}
