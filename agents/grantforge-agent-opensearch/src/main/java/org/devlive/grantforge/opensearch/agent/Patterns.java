// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.BasicAutomata;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.RunAutomaton;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.Policy;
import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Which index patterns of the policies the agent may grant as patterns, so that indices created later, such as
 * tomorrow's logs, are reachable at once: those across which every policy decides the same. A pattern holds the same
 * everywhere when every policy of any kind whose indices overlap it covers it whole; each policy's indices, a union of
 * wildcards or everything but them, and each pattern are finite automata, whose intersections and inclusions decide
 * this exactly. A name without wildcards is such a pattern of one index. Every index such a pattern reaches is then
 * decided as the pattern itself is, as {@code *} matches a {@code *} too, and two such patterns that overlap are decided
 * alike, so that one role entry may hold them both.
 */
final class Patterns
{
    /** What may not appear in a pattern the agent grants: what OpenSearch reads as more than a name or a wildcard. */
    private static final Pattern UNGRANTABLE = Pattern.compile("^[-_+]|[\\\\/\"<>|,# :${}]|^$");

    private final List<Automaton> regions = new ArrayList<>();
    private final SortedSet<String> candidates = new TreeSet<>();
    private final Map<String, RunAutomaton> matchers = new ConcurrentHashMap<>();

    /**
     * Reads the patterns of a snapshot.
     *
     * @param snapshot the policies
     * @param level the resource level of indices
     */
    Patterns(Snapshot snapshot, String level)
    {
        List<Policy> all = new ArrayList<>(snapshot.policies());
        all.addAll(snapshot.maskPolicies());
        all.addAll(snapshot.rowFilterPolicies());
        for (Policy policy : all) {
            ResourceSpec spec = policy.resources().get(level);
            if (spec != null) {
                regions.add(region(spec));
            }
        }
        SortedSet<String> values = new TreeSet<>();
        for (Policy policy : snapshot.policies()) {
            ResourceSpec spec = policy.resources().get(level);
            if (spec != null && !spec.excludes()) {
                values.addAll(spec.values());
            }
        }
        for (String value : values) {
            if (grantable(value) && uniform(value)) {
                candidates.add(value);
            }
        }
    }

    /**
     * Returns the policy values the agent may grant as patterns.
     *
     * @return them, sorted
     */
    SortedSet<String> candidates()
    {
        return candidates;
    }

    /**
     * Returns whether OpenSearch reads a value as an index name or a pattern of {@code *} and {@code ?} only.
     *
     * @param value the value
     * @return {@code false} for values with characters that no index name has, or that OpenSearch reads otherwise
     */
    static boolean grantable(String value)
    {
        return !UNGRANTABLE.matcher(value).find();
    }

    /**
     * Returns whether every policy decides the same across a pattern.
     *
     * @param pattern the pattern
     * @return {@code true} when every policy whose indices overlap it covers it whole
     */
    boolean uniform(String pattern)
    {
        Automaton reached = glob(pattern);
        for (Automaton region : regions) {
            if (!reached.intersection(region).isEmpty() && !reached.subsetOf(region)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the automaton of a wildcard: {@code *} is any run of characters, {@code ?} one character, anything else
     * itself.
     *
     * @param value the wildcard
     * @return the automaton of the names it matches
     */
    static Automaton glob(String value)
    {
        List<Automaton> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (char each : value.toCharArray()) {
            if (each == '*' || each == '?') {
                if (literal.length() > 0) {
                    parts.add(BasicAutomata.makeString(literal.toString()));
                    literal.setLength(0);
                }
                parts.add(each == '*' ? BasicAutomata.makeAnyString() : BasicAutomata.makeAnyChar());
            }
            else {
                literal.append(each);
            }
        }
        if (literal.length() > 0 || parts.isEmpty()) {
            parts.add(BasicAutomata.makeString(literal.toString()));
        }
        return BasicOperations.concatenate(parts);
    }

    /**
     * Returns the automaton of a policy's values: the union of its wildcards, or everything else when it excludes them.
     *
     * @param spec the values
     * @return the automaton of the names it covers
     */
    static Automaton region(ResourceSpec spec)
    {
        List<Automaton> values = new ArrayList<>();
        for (String value : spec.values()) {
            values.add(glob(value));
        }
        Automaton union = BasicOperations.union(values);
        return spec.excludes() ? union.complement() : union;
    }

    /**
     * Returns whether two wildcards match some name alike.
     *
     * @param first a wildcard
     * @param second another
     * @return {@code true} when some name matches both
     */
    static boolean overlap(String first, String second)
    {
        return !glob(first).intersection(glob(second)).isEmpty();
    }

    /**
     * Returns whether a wildcard matches a name.
     *
     * @param pattern the wildcard
     * @param name the name
     * @return {@code true} when it does
     */
    boolean matches(String pattern, String name)
    {
        return matchers.computeIfAbsent(pattern, value -> new RunAutomaton(glob(value))).run(name);
    }

    /**
     * Returns whether a policy's values cover a name.
     *
     * @param spec the values
     * @param name the name
     * @return {@code true} when a value matches it, or none does of values that exclude
     */
    boolean covers(ResourceSpec spec, String name)
    {
        boolean any = false;
        for (String value : spec.values()) {
            if (matches(value, name)) {
                any = true;
                break;
            }
        }
        return any != spec.excludes();
    }

    /**
     * Returns the first candidate that matches a name.
     *
     * @param name the name
     * @return the candidate, or {@code null} when none does
     */
    @Nullable String covering(String name)
    {
        for (String candidate : candidates) {
            if (matches(candidate, name)) {
                return candidate;
            }
        }
        return null;
    }
}
