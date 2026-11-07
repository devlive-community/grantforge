// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the policies give one user in Redis's terms: the selectors every node holds for it, and the nodes it may monitor,
 * which hold one selector more.
 *
 * @param selectors the selectors of every node
 * @param monitored the nodes the user may monitor, as {@code host:port}
 */
record Plan(List<Selector> selectors, Set<String> monitored)
{
    /** A plan that gives nothing. */
    static final Plan NOTHING = new Plan(List.of(), Set.of());

    /** Copies everything. */
    Plan
    {
        selectors = List.copyOf(selectors);
        monitored = Set.copyOf(monitored);
    }

    /**
     * Returns the selectors one node holds for the user.
     *
     * @param node the node
     * @return them, sorted by their rules
     */
    List<Selector> on(String node)
    {
        List<Selector> held = new ArrayList<>(selectors);
        if (monitored.contains(node)) {
            held.add(Selector.of(Selector.MONITOR, new TreeSet<>(), new TreeSet<>()));
        }
        held.sort(Comparator.comparing(Selector::rule));
        return List.copyOf(held);
    }
}
