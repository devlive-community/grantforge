// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.Policy;
import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Turns the policies of a snapshot into what each user is granted in Elasticsearch. The policies' index patterns that
 * every policy decides the same across are granted as patterns, so that indices created later are reachable at once,
 * and overlapping ones in one entry; every other index and data stream the cluster has is granted by name. An index a
 * user may read as a whole, with nothing below it that could take reading away, is read without field level security;
 * otherwise the user reads the fields a policy allows, of those the documents have and those the policies name. An
 * index read with a row filter shows only the documents its query matches; a query Elasticsearch would not run shows
 * none. Below Platinum Elasticsearch turns off every role that restricts fields or documents, so there a read that
 * needs either is not granted at all, never more than the policies allow, and the log says why.
 */
final class Planner
{
    static final String INDEX = "index";
    static final String FIELD = "field";
    static final String CLUSTER = "cluster";
    static final String READ = "read";

    /** The index privilege each access type of an index is granted with: Elasticsearch's own, by the same name. */
    static final List<String> INDEX_PRIVILEGES = List.of("index", "delete", "create_index", "delete_index", "view_index_metadata", "monitor", "manage");
    /** The cluster privilege each access type of the cluster is granted with. */
    static final Map<String, String> CLUSTER_PRIVILEGES = privileges("cluster_monitor", "monitor", "create_snapshot", "create_snapshot", "manage_slm",
            "manage_slm", "manage_index_templates", "manage_index_templates", "manage_ingest_pipelines", "manage_ingest_pipelines", "manage_ilm",
            "manage_ilm");
    /** The query that matches no document, for a row filter Elasticsearch would not run. */
    static final String NOTHING = "{\"bool\":{\"must_not\":{\"match_all\":{}}}}";

    private final Snapshot snapshot;
    private final Inventory inventory;
    private final Instant now;
    private final Function<String, @Nullable String> refusal;
    private final boolean restricting;
    private final Patterns patterns;
    private final List<Unit> units;

    /**
     * What one entry grants: a group of overlapping patterns, or one index or data stream no pattern reaches.
     *
     * @param indices the names and patterns
     * @param representative the name or pattern the policies are asked about
     * @param target what the fields are read of
     */
    record Unit(SortedSet<String> indices, String representative, String target)
    {
    }

    /**
     * Prepares the plans of a snapshot.
     *
     * @param snapshot the policies
     * @param inventory the indices and data streams
     * @param now the moment whose policies apply
     * @param refusal says why Elasticsearch would not run a query, or {@code null} when it would
     * @param restricting whether the license lets roles restrict fields and documents
     */
    Planner(Snapshot snapshot, Inventory inventory, Instant now, Function<String, @Nullable String> refusal, boolean restricting)
    {
        this.snapshot = snapshot;
        this.inventory = inventory;
        this.now = now;
        this.refusal = refusal;
        this.restricting = restricting;
        this.patterns = new Patterns(snapshot, INDEX);
        this.units = group();
    }

    private static Map<String, String> privileges(String... pairs)
    {
        Map<String, String> privileges = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            privileges.put(pairs[index], pairs[index + 1]);
        }
        return Map.copyOf(privileges);
    }

    // A unit per group of patterns and per name no pattern reaches.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private List<Unit> group()
    {
        List<String> candidates = new ArrayList<>(patterns.candidates());
        int[] group = new int[candidates.size()];
        for (int index = 0; index < group.length; index++) {
            group[index] = index;
        }
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                if (Patterns.overlap(candidates.get(first), candidates.get(second))) {
                    int from = root(group, second);
                    int to = root(group, first);
                    group[from] = to;
                }
            }
        }
        Map<Integer, SortedSet<String>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            grouped.computeIfAbsent(root(group, index), ignored -> new TreeSet<>()).add(candidates.get(index));
        }
        List<Unit> found = new ArrayList<>();
        for (SortedSet<String> indices : grouped.values()) {
            found.add(new Unit(indices, indices.first(), String.join(",", indices)));
        }
        for (String name : inventory.names()) {
            if (patterns.covering(name) == null) {
                found.add(new Unit(new TreeSet<>(List.of(name)), name, name));
            }
        }
        return found;
    }

    private static int root(int[] group, int index)
    {
        int at = index;
        while (group[at] != at) {
            at = group[at];
        }
        return at;
    }

    /**
     * Returns what each entry grants.
     *
     * @return the units
     */
    List<Unit> units()
    {
        return List.copyOf(units);
    }

    /**
     * Plans what a user is granted.
     *
     * @param account the user
     * @return the plan
     */
    // A key and a set of names per entry granted.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Plan plan(Account account)
    {
        SortedSet<String> cluster = new TreeSet<>();
        if (!inventory.cluster().isEmpty()) {
            CLUSTER_PRIVILEGES.forEach((type, privilege) -> {
                if (allowed(account, type, CLUSTER, inventory.cluster(), null)) {
                    cluster.add(privilege);
                }
            });
        }
        Map<Plan.Entry, SortedSet<String>> merged = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (Unit unit : units) {
            Plan.Entry entry = entry(account, unit, notes);
            if (entry == null) {
                continue;
            }
            Plan.Entry key = new Plan.Entry(new TreeSet<>(), entry.privileges(), entry.fields(), entry.documents());
            merged.computeIfAbsent(key, ignored -> new TreeSet<>()).addAll(entry.indices());
        }
        List<Plan.Entry> entries = new ArrayList<>();
        merged.forEach((key, indices) -> entries.add(new Plan.Entry(indices, key.privileges(), key.fields(), key.documents())));
        return new Plan(cluster, entries, notes);
    }

    private Plan.@Nullable Entry entry(Account account, Unit unit, List<String> notes)
    {
        String name = unit.representative();
        if (!allowed(account, AccessRequest.ANY_ACCESS, INDEX, name, null)) {
            return null;
        }
        SortedSet<String> privileges = new TreeSet<>();
        for (String type : INDEX_PRIVILEGES) {
            if (allowed(account, type, INDEX, name, null)) {
                privileges.add(type);
            }
        }
        boolean whole = allowed(account, READ, INDEX, name, null) && !snapshot.restrictedBelow(request(account, READ, INDEX, name, null));
        SortedSet<String> fields = new TreeSet<>();
        if (!whole) {
            for (String field : fields(unit)) {
                if (allowed(account, READ, INDEX, name, field)) {
                    fields.add(field);
                }
            }
        }
        if (whole || !fields.isEmpty()) {
            RowFilter filter = snapshot.rowFilter(request(account, READ, INDEX, name, null));
            if (restricting) {
                privileges.add(READ);
                return new Plan.Entry(unit.indices(), privileges, fields, documents(account, unit, filter, notes));
            }
            if (whole && filter == null) {
                privileges.add(READ);
            }
            else {
                notes.add("GrantForge policies give Elasticsearch user " + account.name() + " " + (filter == null ? "some fields" : "some documents")
                        + " of " + unit.target() + ", which needs document and field level security, and the cluster's license is below Platinum;"
                        + " the user may not read " + unit.target() + " rather than read more than the policies allow");
            }
            fields.clear();
        }
        return privileges.isEmpty() ? null : new Plan.Entry(unit.indices(), privileges, fields, null);
    }

    /** The fields of what a unit reaches, with the fields the policies that cover it name. */
    private SortedSet<String> fields(Unit unit)
    {
        SortedSet<String> fields = new TreeSet<>(inventory.fields(unit.target()));
        for (Policy policy : snapshot.policies()) {
            ResourceSpec indices = policy.resources().get(INDEX);
            ResourceSpec named = policy.resources().get(FIELD);
            if (indices == null || named == null || named.excludes() || !patterns.covers(indices, unit.representative())) {
                continue;
            }
            for (String value : named.values()) {
                if (value.indexOf('*') < 0 && value.indexOf('?') < 0) {
                    fields.add(value);
                }
            }
        }
        return fields;
    }

    private @Nullable String documents(Account account, Unit unit, @Nullable RowFilter filter, List<String> notes)
    {
        if (filter == null) {
            return null;
        }
        String why = refusal.apply(filter.expression());
        if (why == null) {
            return filter.expression();
        }
        notes.add("GrantForge policy " + filter.policyId() + " filters the documents of " + unit.target() + " for Elasticsearch user " + account.name()
                + " with a query Elasticsearch would not run (" + why + "); the user sees none of them");
        return NOTHING;
    }

    private boolean allowed(Account account, String type, String level, String name, @Nullable String field)
    {
        return snapshot.decide(request(account, type, level, name, field)).allowed();
    }

    private AccessRequest request(Account account, String type, String level, String name, @Nullable String field)
    {
        AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0]))
                .resource(level, name).time(now);
        if (field != null) {
            request.resource(FIELD, field);
        }
        return request.build();
    }
}
