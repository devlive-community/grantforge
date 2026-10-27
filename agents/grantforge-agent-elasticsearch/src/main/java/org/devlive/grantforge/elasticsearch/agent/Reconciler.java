// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.core5.net.URIBuilder;
import org.jspecify.annotations.Nullable;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Brings Elasticsearch in step with the plans: writes each user's role where it is missing or differs, gives it to the
 * user and takes back the roles of users granted nothing any more. A native user holds the role among its own roles,
 * which the agent keeps as they are; any other user gets it through a role mapping of the user's name, which every realm
 * but the native and file realms applies. Roles are written before they are given and taken back before they are
 * removed, so that no user is left with a role that does not exist. A change Elasticsearch refuses is logged and does
 * not stop the others.
 */
final class Reconciler
{
    static final String ROLES = "/_security/role";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    private final Elasticsearch search;
    private final String prefix;

    /**
     * What one reconciliation changed.
     *
     * @param users the users given a role
     * @param created the roles created
     * @param updated the roles written again
     * @param dropped the roles removed
     * @param assigned the role mappings written or removed, and the native users whose roles changed
     * @param failed the changes Elasticsearch refused
     */
    record Outcome(int users, int created, int updated, int dropped, int assigned, int failed)
    {
        /**
         * Returns whether the cluster changed or was asked to.
         *
         * @return {@code true} when something was written, removed or refused
         */
        boolean changed()
        {
            return created + updated + dropped + assigned + failed > 0;
        }
    }

    Reconciler(Elasticsearch search, String prefix)
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
     * @throws ElasticsearchFailure when the cluster cannot be reached
     */
    // A set of roles per native user.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Outcome reconcile(ServerState state, @Nullable Function<Account, Plan> plans)
    {
        Map<String, ObjectNode> roles = new LinkedHashMap<>();
        if (plans != null) {
            for (Account account : state.accounts()) {
                Plan plan = plans.apply(account);
                for (String note : plan.notes()) {
                    LOG.warning(note);
                }
                if (!plan.empty()) {
                    roles.put(role(account.name()), plan.role(account.name()));
                }
            }
        }
        ObjectNode writes = JSON.createObjectNode();
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
            writes.set(wanted.getKey(), wanted.getValue());
        }
        int failed = writeRoles(writes);
        int assigned = 0;
        for (Map.Entry<String, ObjectNode> wanted : roles.entrySet()) {
            String user = wanted.getValue().path("metadata").path(ServerState.OWNER).asText();
            JsonNode mapping = state.mappings().get(wanted.getKey());
            if (mapping == null || !mapped(mapping, wanted.getKey(), user)) {
                assigned++;
                failed += send("PUT", path("_security", "role_mapping", wanted.getKey()), mapping(wanted.getKey(), user));
            }
        }
        for (ServerState.NativeUser user : state.nativeUsers().values()) {
            if (user.reserved() || !wanted(state, user.name())) {
                continue;
            }
            // The user's own roles, the agent's for the user when it has one, and none of the agent's other roles.
            SortedSet<String> held = new TreeSet<>(user.roles());
            held.removeAll(state.agentRoles().keySet());
            held.remove(role(user.name()));
            if (roles.containsKey(role(user.name()))) {
                held.add(role(user.name()));
            }
            if (!held.equals(user.roles())) {
                assigned++;
                failed += send("PUT", path("_security", "user", user.name()), withRoles(user, held));
            }
        }
        SortedSet<String> stale = new TreeSet<>(state.agentRoles().keySet());
        stale.removeAll(roles.keySet());
        for (String role : stale) {
            if (state.mappings().containsKey(role)) {
                assigned++;
                failed += send("DELETE", path("_security", "role_mapping", role), null);
            }
        }
        failed += removeRoles(stale);
        return new Outcome(roles.size(), created, updated, stale.size(), assigned, failed);
    }

    /** Whether the agent decides the roles of a native user: one it keeps roles for, or one it kept a role for before. */
    private static boolean wanted(ServerState state, String user)
    {
        return state.accounts().stream().anyMatch(account -> account.name().equals(user)) || state.agentRoles().containsValue(user);
    }

    /** A native user as the user API takes it, with its roles replaced and everything else kept. */
    private static ObjectNode withRoles(ServerState.NativeUser user, SortedSet<String> roles)
    {
        ObjectNode body = JSON.createObjectNode();
        ArrayNode list = body.putArray("roles");
        roles.forEach(list::add);
        for (String field : List.of("full_name", "email")) {
            JsonNode value = user.body().path(field);
            if (value.isTextual()) {
                body.put(field, value.asText());
            }
        }
        JsonNode metadata = user.body().path("metadata");
        body.set("metadata", metadata.isObject() ? metadata.deepCopy() : JSON.createObjectNode());
        body.put("enabled", user.body().path("enabled").asBoolean(true));
        return body;
    }

    private static ObjectNode mapping(String role, String user)
    {
        ObjectNode mapping = JSON.createObjectNode();
        mapping.put("enabled", true);
        mapping.putArray("roles").add(role);
        mapping.putObject("rules").putObject("field").put("username", user);
        mapping.putObject("metadata").put(ServerState.OWNER, user);
        return mapping;
    }

    /** Whether a mapping held gives the role, and it alone, to the user alone. */
    private static boolean mapped(JsonNode held, String role, String user)
    {
        JsonNode name = held.path("rules").path("field").path("username");
        boolean only = held.path("rules").size() == 1 && held.path("rules").path("field").size() == 1
                && (name.isTextual() && user.equals(name.asText()) || name.isArray() && name.size() == 1 && user.equals(name.path(0).asText()));
        return held.path("enabled").asBoolean(false) && ServerState.texts(held.path("roles")).equals(Set.of(role)) && only
                && held.path("role_templates").isEmpty();
    }

    /**
     * Returns what a role grants, comparable whatever order Elasticsearch keeps its values in and whatever it adds of
     * its own, such as {@code transient_metadata}.
     *
     * @param role the role
     * @return its canonical JSON
     */
    static String canonical(JsonNode role)
    {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("description", role.path("description").asText(""));
        canonical.put("owner", role.path("metadata").path(ServerState.OWNER).asText(""));
        sorted(canonical.putArray("cluster"), role.path("cluster"));
        List<String> entries = new ArrayList<>();
        for (JsonNode entry : role.path("indices")) {
            ObjectNode each = JSON.createObjectNode();
            sorted(each.putArray("names"), entry.path("names"));
            sorted(each.putArray("privileges"), entry.path("privileges"));
            sorted(each.putArray("grant"), entry.path("field_security").path("grant"));
            sorted(each.putArray("except"), entry.path("field_security").path("except"));
            String documents = entry.path("query").asText("");
            if (!documents.isEmpty()) {
                each.put("query", documents);
            }
            each.put("restricted", entry.path("allow_restricted_indices").asBoolean(false));
            entries.add(each.toString());
        }
        entries.sort(null);
        ArrayNode indices = canonical.putArray("indices");
        entries.forEach(indices::add);
        // Anything else a role may hold, which the agent's roles never do; a role edited by hand to hold it is rewritten.
        for (String other : List.of("applications", "run_as", "remote_indices", "remote_cluster", "global")) {
            JsonNode value = role.path(other);
            if (!value.isMissingNode() && !value.isNull() && !value.isEmpty()) {
                canonical.set(other, value);
            }
        }
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

    /** Writes roles with one request; returns how many Elasticsearch refused. */
    private int writeRoles(ObjectNode roles)
    {
        if (roles.isEmpty()) {
            return 0;
        }
        ObjectNode body = JSON.createObjectNode();
        body.set("roles", roles);
        try {
            return refused(search.send("POST", ROLES, Map.of(), body), "write");
        }
        catch (ElasticsearchFailure failed) {
            if (failed.unreachable()) {
                throw failed;
            }
            LOG.warning("Elasticsearch refused to write the GrantForge agent's roles " + names(roles) + ": " + failed.getMessage());
            return roles.size();
        }
    }

    /** Removes roles with one request; returns how many Elasticsearch refused. */
    private int removeRoles(SortedSet<String> roles)
    {
        if (roles.isEmpty()) {
            return 0;
        }
        ObjectNode body = JSON.createObjectNode();
        ArrayNode names = body.putArray("names");
        roles.forEach(names::add);
        try {
            return refused(search.send("DELETE", ROLES, Map.of(), body), "remove");
        }
        catch (ElasticsearchFailure failed) {
            if (failed.unreachable()) {
                throw failed;
            }
            LOG.warning("Elasticsearch refused to remove the GrantForge agent's roles " + roles + ": " + failed.getMessage());
            return roles.size();
        }
    }

    /** Logs the roles a bulk request names among its errors and returns how many there are. */
    private static int refused(JsonNode answer, String what)
    {
        JsonNode details = answer.path("errors").path("details");
        Iterator<Map.Entry<String, JsonNode>> errors = details.properties().iterator();
        int count = 0;
        while (errors.hasNext()) {
            Map.Entry<String, JsonNode> error = errors.next();
            LOG.warning("Elasticsearch refused to " + what + " the GrantForge agent's role " + error.getKey() + ": "
                    + error.getValue().path("reason").asText(error.getValue().toString()));
            count++;
        }
        return Math.max(count, answer.path("errors").path("count").asInt(0));
    }

    private static List<String> names(ObjectNode roles)
    {
        List<String> names = new ArrayList<>();
        roles.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /**
     * Builds an encoded path from its segments, as names of users and roles may hold any character.
     *
     * @param segments the segments
     * @return the path
     */
    static String path(String... segments)
    {
        try {
            return new URIBuilder().setPathSegments(segments).build().getRawPath();
        }
        catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("no path of " + List.of(segments), impossible);
        }
    }

    /** Sends one change; returns 1 when Elasticsearch refused it. */
    private int send(String method, String path, @Nullable JsonNode body)
    {
        try {
            search.send(method, path, Map.of(), body);
            return 0;
        }
        catch (ElasticsearchFailure refused) {
            if (refused.unreachable()) {
                throw refused;
            }
            LOG.warning("Elasticsearch refused a change of the GrantForge agent: " + method + " " + path + ": " + refused.getMessage());
            return 1;
        }
    }
}
