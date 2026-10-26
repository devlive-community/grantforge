// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.Policy;
import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.jspecify.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns the policies of a snapshot into what each user is granted in OpenSearch. The policies' index patterns that every
 * policy decides the same across are granted as patterns, so that indices created later are reachable at once, and
 * overlapping ones in one entry; every other index and data stream the cluster has is granted by name. An index a user
 * may read as a whole, with nothing below it that could take reading away, is read without field level security;
 * otherwise the user reads the fields a policy allows, of those the documents have and those the policies name. A field
 * read with a mask becomes a masked field, and an index read with a row filter shows only the documents its query
 * matches; a query OpenSearch would not run shows none. Reading needs the cluster's read-only composite operations, such
 * as multi-search, and writing its composite operations, such as bulk; they come with it.
 */
final class Planner
{
    static final String INDEX = "index";
    static final String FIELD = "field";
    static final String CLUSTER = "cluster";
    static final String READ = "read";

    /** The action group each access type of an index is granted with. */
    static final Map<String, String> INDEX_GROUPS = groups("index", "index", "delete", "delete", "create_index", "create_index", "manage_aliases",
            "manage_aliases", "monitor", "indices_monitor", "manage", "manage");
    /** The action group each access type of the cluster is granted with. */
    static final Map<String, String> CLUSTER_GROUPS = groups("cluster_monitor", "cluster_monitor", "manage_snapshots", "manage_snapshots",
            "manage_templates", "cluster_manage_index_templates", "manage_pipelines", "cluster_manage_pipelines");
    static final String READ_GROUP = "read";
    static final String READ_COMPOSITE = "cluster_composite_ops_ro";
    static final String WRITE_COMPOSITE = "cluster_composite_ops";
    /** The query that matches no document, for a row filter OpenSearch would not run. */
    static final String NOTHING = "{\"bool\":{\"must_not\":{\"match_all\":{}}}}";

    /** A custom mask: a hash algorithm, or regular expressions with their replacements. */
    private static final Pattern ALGORITHM = Pattern.compile("[A-Za-z0-9-]+");

    private final Snapshot snapshot;
    private final Inventory inventory;
    private final Instant now;
    private final Function<String, @Nullable String> refusal;
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
     * @param refusal says why OpenSearch would not run a query, or {@code null} when it would
     */
    Planner(Snapshot snapshot, Inventory inventory, Instant now, Function<String, @Nullable String> refusal)
    {
        this.snapshot = snapshot;
        this.inventory = inventory;
        this.now = now;
        this.refusal = refusal;
        this.patterns = new Patterns(snapshot, INDEX);
        this.units = group();
    }

    private static Map<String, String> groups(String... pairs)
    {
        Map<String, String> groups = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            groups.put(pairs[index], pairs[index + 1]);
        }
        return Map.copyOf(groups);
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
            CLUSTER_GROUPS.forEach((type, group) -> {
                if (allowed(account, type, CLUSTER, inventory.cluster(), null)) {
                    cluster.add(group);
                }
            });
        }
        Map<Plan.Entry, SortedSet<String>> merged = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        boolean reads = false;
        boolean writes = false;
        for (Unit unit : units) {
            Plan.Entry entry = entry(account, unit, notes);
            if (entry == null) {
                continue;
            }
            reads |= entry.actions().contains(READ_GROUP);
            writes |= entry.actions().contains("index") || entry.actions().contains("delete");
            Plan.Entry key = new Plan.Entry(new TreeSet<>(), entry.actions(), entry.fields(), entry.masked(), entry.documents());
            merged.computeIfAbsent(key, ignored -> new TreeSet<>()).addAll(entry.indices());
        }
        if (writes) {
            cluster.add(WRITE_COMPOSITE);
        }
        else if (reads) {
            cluster.add(READ_COMPOSITE);
        }
        List<Plan.Entry> entries = new ArrayList<>();
        merged.forEach((key, indices) -> entries.add(new Plan.Entry(indices, key.actions(), key.fields(), key.masked(), key.documents())));
        return new Plan(cluster, entries, notes);
    }

    private Plan.@Nullable Entry entry(Account account, Unit unit, List<String> notes)
    {
        String name = unit.representative();
        if (!allowed(account, AccessRequest.ANY_ACCESS, INDEX, name, null)) {
            return null;
        }
        SortedSet<String> actions = new TreeSet<>();
        INDEX_GROUPS.forEach((type, group) -> {
            if (allowed(account, type, INDEX, name, null)) {
                actions.add(group);
            }
        });
        boolean whole = allowed(account, READ, INDEX, name, null) && !snapshot.restrictedBelow(request(account, READ, INDEX, name, null));
        SortedSet<String> readable = new TreeSet<>();
        if (!whole) {
            for (String field : fields(unit, snapshot.policies())) {
                if (allowed(account, READ, INDEX, name, field)) {
                    readable.add(field);
                }
            }
        }
        SortedSet<String> fields = new TreeSet<>(readable);
        SortedSet<String> masked = new TreeSet<>();
        String documents = null;
        if (whole || !readable.isEmpty()) {
            SortedSet<String> maskable = whole ? fields(unit, snapshot.maskPolicies()) : readable;
            for (String field : maskable) {
                DataMask mask = snapshot.mask(request(account, READ, INDEX, name, field));
                if (mask == null) {
                    continue;
                }
                String expression = masked(mask, field);
                if (expression != null) {
                    masked.add(expression);
                }
                else if (mask.transformer() != null) {
                    notes.add("GrantForge policy " + mask.policyId() + " masks " + field + " of " + unit.target() + " for OpenSearch user "
                            + account.name() + " with a mask OpenSearch does not know; the user may not read the field");
                    if (whole) {
                        fields.add("~" + field);
                    }
                    else {
                        fields.remove(field);
                    }
                }
            }
            if (!whole && fields.isEmpty()) {
                masked.clear();
            }
            else {
                actions.add(READ_GROUP);
                documents = documents(account, unit, notes);
            }
        }
        return actions.isEmpty() ? null : new Plan.Entry(unit.indices(), actions, fields, masked, documents);
    }

    /** The fields of what a unit reaches, with the fields the policies that cover it name. */
    private SortedSet<String> fields(Unit unit, List<Policy> policies)
    {
        SortedSet<String> fields = new TreeSet<>(inventory.fields(unit.target()));
        for (Policy policy : policies) {
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

    private @Nullable String documents(Account account, Unit unit, List<String> notes)
    {
        RowFilter filter = snapshot.rowFilter(request(account, READ, INDEX, unit.representative(), null));
        if (filter == null) {
            return null;
        }
        String why = refusal.apply(filter.expression());
        if (why == null) {
            return filter.expression();
        }
        notes.add("GrantForge policy " + filter.policyId() + " filters the documents of " + unit.target() + " for OpenSearch user " + account.name()
                + " with a query OpenSearch would not run (" + why + "); the user sees none of them");
        return NOTHING;
    }

    /**
     * Writes a mask as the security plugin's masked field.
     *
     * @param mask the mask
     * @param field the field
     * @return the masked field, or {@code null} when the mask leaves the value as it is, or is no hash algorithm or
     *         regular expressions with replacements that the security plugin knows
     */
    static @Nullable String masked(DataMask mask, String field)
    {
        String expression;
        try {
            expression = mask.expression(field);
        }
        catch (IllegalStateException missing) {
            return null;
        }
        if (expression == null) {
            return null;
        }
        if (!expression.startsWith(field)) {
            return null;
        }
        String rest = expression.substring(field.length());
        if (rest.isEmpty()) {
            return expression;
        }
        if (!rest.startsWith("::")) {
            return null;
        }
        String[] parts = rest.substring(2).split("::", -1);
        if (parts.length == 1) {
            return algorithm(parts[0]) ? expression : null;
        }
        if (parts.length % 2 != 0) {
            return null;
        }
        for (int index = 0; index < parts.length; index += 2) {
            String regex = parts[index];
            if (regex.length() < 3 || !regex.startsWith("/") || !regex.endsWith("/")) {
                return null;
            }
            try {
                Pattern.compile(regex.substring(1, regex.length() - 1));
            }
            catch (PatternSyntaxException invalid) {
                return null;
            }
        }
        return expression;
    }

    private static boolean algorithm(String name)
    {
        if (!ALGORITHM.matcher(name).matches()) {
            return false;
        }
        try {
            MessageDigest.getInstance(name);
            return true;
        }
        catch (NoSuchAlgorithmException unknown) {
            return false;
        }
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
