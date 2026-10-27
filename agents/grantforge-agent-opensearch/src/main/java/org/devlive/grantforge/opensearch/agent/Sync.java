// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.SyncLoop;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One sync of OpenSearch with the policies: reads what the cluster holds, plans each user's role from the snapshot at
 * the present moment, so that policies with validity periods start and end on time, applies the plans and warns of
 * roles outside GrantForge that give what the policies deny. Row filters are queries OpenSearch checks before they are
 * granted. Without a snapshot nothing changes; a snapshot of a service that is not in use, or of another service type,
 * grants nothing.
 */
final class Sync
        extends SyncLoop<ServerState, Reconciler.Outcome>
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "opensearch";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenSearch search;
    private final AgentConfig config;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<Inventory> inventory = new AtomicReference<>(Inventory.EMPTY);

    Sync(OpenSearch search, AgentConfig config, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        super(new Target("OpenSearch", SERVICE_TYPE, "OpenSearch's own roles"), snapshots, clock);
        this.search = search;
        this.config = config;
        this.reconciler = new Reconciler(search, config.rolePrefix());
    }

    /**
     * Reads what the cluster holds and remembers its users' groups and what policies can name.
     *
     * @throws OpenSearchFailure when the cluster fails a request or cannot be reached
     */
    @Override
    protected ServerState read(Snapshot snapshot)
    {
        ServerState state = ServerState.read(search, config.rolePrefix(), config.unmanaged(), snapshot.users());
        Map<String, Set<String>> held = new HashMap<>();
        state.internalUsers().forEach((name, user) -> held.put(name, user.backendRoles()));
        groups.set(held);
        inventory.set(state.inventory());
        return state;
    }

    /**
     * Plans each user's access from the snapshot, applies the plans and warns of access outside GrantForge that gives what
     * the policies deny.
     *
     * @throws OpenSearchFailure when the cluster fails a request or cannot be reached
     */
    @Override
    protected Reconciler.Outcome apply(ServerState state, @Nullable Snapshot snapshot, Instant now)
    {
        Function<Account, Plan> plans = null;
        if (snapshot != null) {
            Map<String, @Nullable String> checked = new HashMap<>();
            Planner planner = new Planner(snapshot, state.inventory(), now, query -> checked.computeIfAbsent(query, this::refusal));
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, plans);
        if (snapshot != null) {
            conflicts.check(state, snapshot, now);
        }
        return outcome;
    }

    @Override
    protected boolean changed(Reconciler.Outcome outcome)
    {
        return outcome.changed();
    }

    @Override
    protected String summary(Reconciler.Outcome outcome)
    {
        return outcome.users() + " users, " + outcome.created() + " roles created, " + outcome.updated() + " updated, " + outcome.dropped()
                + " dropped, " + outcome.mappings() + " role mappings changed" + (outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : "");
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
