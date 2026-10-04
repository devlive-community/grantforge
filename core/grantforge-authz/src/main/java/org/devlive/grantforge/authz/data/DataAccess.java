// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataAction;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Everything a reader's roles say about data: who the reader is, and per entity and action the rules that allow rows and
 * those that deny them. Without an allowing rule an entity's rows are hidden.
 *
 * @param subject the reader
 * @param rules the rules by entity code and action
 */
public record DataAccess(DataSubject subject, Map<Key, Rules> rules)
{
    private static final Rules NONE = new Rules(List.of(), List.of());

    /** Copies the rules. */
    public DataAccess
    {
        requireNonNull(subject, "subject");
        rules = Map.copyOf(rules);
    }

    /**
     * Returns the rules of an entity and action.
     *
     * @param entityCode the entity
     * @param action the action
     * @return its allowing and denying rules; none if no role says anything
     */
    public Rules rules(String entityCode, DataAction action)
    {
        return rules.getOrDefault(new Key(entityCode, action), NONE);
    }

    /**
     * An entity and an action.
     *
     * @param entityCode the entity
     * @param action the action
     */
    public record Key(String entityCode, DataAction action)
    {
    }

    /**
     * The rules on an entity and action.
     *
     * @param allow the rules whose rows the reader may use
     * @param deny the rules whose rows the reader may not use, whatever allows them
     */
    public record Rules(List<DataRule> allow, List<DataRule> deny)
    {
        /** Copies the rules. */
        public Rules
        {
            allow = List.copyOf(allow);
            deny = List.copyOf(deny);
        }
    }
}
