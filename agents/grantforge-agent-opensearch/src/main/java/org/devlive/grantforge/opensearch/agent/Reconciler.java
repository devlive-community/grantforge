// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Brings the security plugin's roles and role mappings in step with the plans: one role per user that is granted
 * something, named after the user and mapped to that user alone, and none for the others. Roles and mappings that
 * differ from their plans, by hand or otherwise, are written again. Each kind of change is one JSON patch, which the
 * security plugin applies at once across the cluster, roles before the mappings that name them and mappings before the
 * roles they name are removed; a patch the security plugin refuses is applied change by change, so that one bad role
 * fails alone.
 */
final class Reconciler
{
    static final String ROLES = "/_plugins/_security/api/roles";
    static final String MAPPINGS = "/_plugins/_security/api/rolesmapping";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    private final OpenSearch search;
    private final String prefix;

    /**
     * What one reconciliation changed.
     *
     * @param users the users given a role
     * @param created the roles created
     * @param updated the roles written again
     * @param dropped the roles removed
     * @param mappings the role mappings written or removed
     * @param failed the changes the security plugin refused
     */
    record Outcome(int users, int created, int updated, int dropped, int mappings, int failed)
    {
        /**
         * Returns whether the cluster changed or was asked to.
         *
         * @return {@code true} when something was written, removed or refused
         */
        boolean changed()
        {
            return created + updated + dropped + mappings + failed > 0;
        }
    }

    Reconciler(OpenSearch search, String prefix)
    {
        this.search = search;
        this.prefix = prefix;
    }

    /**
     * Returns the role the agent keeps for a user.
     *
     * @param user the user
     * @return the role's name
     */
    String role(String user)
    {
        return prefix + user;
    }

    /**
     * Brings the cluster in step.
     *
     * @param state what the cluster holds
     * @param plans what each user is granted, or {@code null} when GrantForge grants nothing
     * @return what changed
     * @throws OpenSearchFailure when the cluster cannot be reached
     */
    Outcome reconcile(ServerState state, @Nullable Function<Account, Plan> plans)
    {
        Map<String, ObjectNode> roles = new LinkedHashMap<>();
        Map<String, ObjectNode> mappings = new LinkedHashMap<>();
        if (plans != null) {
            for (Account account : state.accounts()) {
                Plan plan = plans.apply(account);
                for (String note : plan.notes()) {
                    LOG.warning(note);
                }
                if (!plan.empty()) {
                    roles.put(role(account.name()), plan.role(account.name()));
                    mappings.put(role(account.name()), mapping(account.name()));
                }
            }
        }
        List<JsonNode> writeRoles = new ArrayList<>();
        int created = 0;
        int updated = 0;
        for (Map.Entry<String, ObjectNode> wanted : roles.entrySet()) {
            JsonNode held = state.roles().get(wanted.getKey());
            if (held == null) {
                created++;
            }
            else if (!canonical(held).equals(canonical(wanted.getValue()))) {
                updated++;
            }
            else {
                continue;
            }
            writeRoles.add(operation("add", wanted.getKey(), wanted.getValue()));
        }
        List<JsonNode> writeMappings = new ArrayList<>();
        for (Map.Entry<String, ObjectNode> wanted : mappings.entrySet()) {
            JsonNode held = state.mappings().get(wanted.getKey());
            if (held == null || !mapped(held, wanted.getValue())) {
                writeMappings.add(operation("add", wanted.getKey(), wanted.getValue()));
            }
        }
        Set<String> stale = new TreeSet<>(state.agentRoles().keySet());
        stale.removeAll(roles.keySet());
        for (String role : stale) {
            if (state.mappings().containsKey(role)) {
                writeMappings.add(operation("remove", role, null));
            }
        }
        List<JsonNode> removeRoles = new ArrayList<>();
        stale.forEach(role -> removeRoles.add(operation("remove", role, null)));
        int failed = patch(ROLES, writeRoles) + patch(MAPPINGS, writeMappings) + patch(ROLES, removeRoles);
        return new Outcome(roles.size(), created, updated, stale.size(), writeMappings.size(), failed);
    }

    private static ObjectNode mapping(String user)
    {
        ObjectNode mapping = JSON.createObjectNode();
        mapping.put("description", ServerState.MARKER + user);
        mapping.putArray("users").add(user);
        return mapping;
    }

    /** Whether a mapping held maps the role to the user alone. */
    private static boolean mapped(JsonNode held, JsonNode wanted)
    {
        return ServerState.texts(held.path("users")).equals(ServerState.texts(wanted.path("users"))) && ServerState.texts(held.path("backend_roles")).isEmpty()
                && ServerState.texts(held.path("hosts")).isEmpty() && ServerState.texts(held.path("and_backend_roles")).isEmpty();
    }

    /**
     * Returns what a role grants, comparable whatever order the security plugin keeps its values in and whatever it adds
     * of its own, such as {@code reserved}.
     *
     * @param role the role
     * @return its canonical JSON
     */
    static String canonical(JsonNode role)
    {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("description", role.path("description").asText(""));
        sorted(canonical.putArray("cluster_permissions"), role.path("cluster_permissions"));
        sorted(canonical.putArray("tenant_permissions"), role.path("tenant_permissions"));
        List<String> entries = new ArrayList<>();
        for (JsonNode entry : role.path("index_permissions")) {
            ObjectNode each = JSON.createObjectNode();
            sorted(each.putArray("index_patterns"), entry.path("index_patterns"));
            sorted(each.putArray("allowed_actions"), entry.path("allowed_actions"));
            sorted(each.putArray("fls"), entry.path("fls"));
            sorted(each.putArray("masked_fields"), entry.path("masked_fields"));
            String documents = entry.path("dls").asText("");
            if (!documents.isEmpty()) {
                each.put("dls", documents);
            }
            entries.add(each.toString());
        }
        entries.sort(null);
        ArrayNode permissions = canonical.putArray("index_permissions");
        entries.forEach(permissions::add);
        return canonical.toString();
    }

    private static void sorted(ArrayNode target, JsonNode values)
    {
        List<String> texts = new ArrayList<>();
        for (JsonNode value : values) {
            texts.add(value.isTextual() ? value.asText() : value.toString());
        }
        texts.sort(null);
        texts.forEach(target::add);
    }

    private static JsonNode operation(String op, String name, @Nullable JsonNode value)
    {
        ObjectNode operation = JSON.createObjectNode();
        operation.put("op", op);
        operation.put("path", JsonPointer.empty().appendProperty(name).toString());
        if (value != null) {
            operation.set("value", value);
        }
        return operation;
    }

    /** Applies changes as one patch, or one by one when the patch is refused; returns how many were refused. */
    private int patch(String api, List<JsonNode> operations)
    {
        if (operations.isEmpty()) {
            return 0;
        }
        try {
            search.send("PATCH", api, Map.of(), JSON.createArrayNode().addAll(operations));
            return 0;
        }
        catch (OpenSearchFailure refused) {
            if (refused.unreachable() || operations.size() == 1) {
                if (refused.unreachable()) {
                    throw refused;
                }
                LOG.warning("The security plugin refused a change of the GrantForge agent: " + operations.get(0).path("op").asText() + " "
                        + operations.get(0).path("path").asText() + ": " + refused.getMessage());
                return 1;
            }
        }
        int failed = 0;
        Set<String> seen = new HashSet<>();
        for (JsonNode operation : operations) {
            if (seen.add(operation.path("path").asText())) {
                failed += patch(api, List.of(operation));
            }
        }
        return failed;
    }
}
