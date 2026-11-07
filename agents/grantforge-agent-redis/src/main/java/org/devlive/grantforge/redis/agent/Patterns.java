// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.BasicAutomata;
import dk.brics.automaton.BasicOperations;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.Policy;
import org.devlive.grantforge.policy.engine.ResourceSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Which key or channel patterns of the policies the agent may grant as Redis's own patterns: those across which every
 * policy decides the same, as Redis cannot be told which keys or channels are not there, and lists none of them to the
 * agent. A pattern holds the same everywhere when every policy whose keys overlap it covers it whole; each policy's
 * keys, a union of wildcards or everything but them, and each pattern are finite automata, whose intersections and
 * inclusions decide this exactly. A name without wildcards is such a pattern of one key. Every key such a pattern
 * reaches is then decided as the pattern itself is, as {@code *} matches a {@code *} too.
 */
final class Patterns
{
    /**
     * What may not appear in a pattern the agent grants: what Redis's patterns read as more than a character or a
     * wildcard, and what an ACL rule cannot hold.
     */
    private static final Pattern UNGRANTABLE = Pattern.compile("[\\[\\]\\\\()\\s]|^$");

    /** The most regions a pattern is cut into to decide it for one user. */
    static final int MAX_REGIONS = 256;

    private final List<Automaton> regions = new ArrayList<>();
    private final SortedSet<String> values = new TreeSet<>();
    private final SortedSet<String> candidates = new TreeSet<>();

    /**
     * Reads the patterns of a snapshot.
     *
     * @param snapshot the policies
     * @param level the resource level of the names
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
     * Returns the values the access policies name, other than those they exclude.
     *
     * @return them, sorted
     */
    SortedSet<String> values()
    {
        return values;
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
     * Returns whether Redis reads a value as a name or a pattern of {@code *} and {@code ?} only, in a rule of its own.
     *
     * @param value the value
     * @return {@code false} for empty values and values with brackets, backslashes, parentheses or white space
     */
    static boolean grantable(String value)
    {
        return !UNGRANTABLE.matcher(value).find();
    }

    /**
     * Returns whether every policy decides the same across a pattern.
     *
     * @param pattern the pattern
     * @return {@code true} when every policy whose names overlap it covers it whole
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
     * Returns whether a pattern is decided the same for one user everywhere it reaches, though other users' policies may
     * decide parts of it differently. The pattern is cut along every policy whose names overlap it into regions each
     * policy covers whole or not at all, at most {@value #MAX_REGIONS} of them; every name of a region is decided alike, so
     * the decision of one name of each tells.
     *
     * @param pattern the pattern
     * @param decision how the user is decided for a name
     * @return {@code true} when every region is decided as the pattern itself is
     */
    // A list per policy the pattern is cut along.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    boolean uniform(String pattern, Predicate<String> decision)
    {
        if (uniform(pattern)) {
            return true;
        }
        List<Automaton> parts = new ArrayList<>(List.of(glob(pattern)));
        for (Automaton region : regions) {
            List<Automaton> cut = new ArrayList<>();
            for (Automaton part : parts) {
                for (Automaton piece : List.of(part.intersection(region), part.minus(region))) {
                    if (!piece.isEmpty()) {
                        cut.add(piece);
                    }
                }
            }
            if (cut.size() > MAX_REGIONS) {
                return false;
            }
            parts = cut;
        }
        boolean decided = decision.test(pattern);
        Automaton named = BasicOperations.concatenate(BasicAutomata.makeAnyChar(), BasicAutomata.makeAnyString());
        for (Automaton part : parts) {
            // A region of the empty name alone holds no key a policy could name.
            String example = part.intersection(named).getShortestExample(true);
            if (example != null && decision.test(example) != decided) {
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
}
