// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the policies give one user in MongoDB's terms: the actions of its role, by what they are on.
 *
 * @param privileges the actions by target, sorted
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
record Plan(SortedMap<Target, SortedSet<String>> privileges)
{
    /** A plan that gives nothing. */
    static final Plan NOTHING = new Plan(new TreeMap<>(Target.ORDER));

    /** Sorts and copies everything, leaving out targets without actions. */
    Plan
    {
        SortedMap<Target, SortedSet<String>> sorted = new TreeMap<>(Target.ORDER);
        privileges.forEach((target, actions) -> {
            if (!actions.isEmpty()) {
                sorted.put(target, Collections.unmodifiableSortedSet(new TreeSet<>(actions)));
            }
        });
        privileges = Collections.unmodifiableSortedMap(sorted);
    }

    /**
     * Builds a plan.
     *
     * @param privileges the actions by target
     * @return the plan
     */
    static Plan of(Map<Target, ? extends SortedSet<String>> privileges)
    {
        SortedMap<Target, SortedSet<String>> sorted = new TreeMap<>(Target.ORDER);
        sorted.putAll(privileges);
        return new Plan(sorted);
    }

    /**
     * Returns whether the plan gives nothing, so that the user needs no role.
     *
     * @return {@code true} if it has no privileges
     */
    boolean empty()
    {
        return privileges.isEmpty();
    }

    /**
     * Writes the privileges as a role holds them.
     *
     * @return the privileges of {@code createRole} and {@code updateRole}
     */
    List<Document> documents()
    {
        List<Document> documents = new ArrayList<>(privileges.size());
        privileges.forEach((target, actions) -> documents.add(new Document("resource", target.document()).append("actions", new ArrayList<>(actions))));
        return documents;
    }
}
