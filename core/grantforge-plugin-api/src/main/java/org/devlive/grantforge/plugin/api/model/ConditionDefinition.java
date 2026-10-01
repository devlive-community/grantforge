// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A condition a policy item may carry, such as an IP range or a time window. The policy engine evaluates it
 * through the evaluator registered under {@code evaluator}.
 *
 * @param name the name, unique within the service type
 * @param label what the console shows
 * @param evaluator the name of the engine's evaluator, for example {@code ip-range}
 * @param options settings passed to the evaluator
 */
public record ConditionDefinition(String name, String label, String evaluator, Map<String, String> options)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if a name is malformed or the label blank
     */
    public ConditionDefinition
    {
        Names.name(name, "condition name");
        label = Names.label(label, "label of condition " + name);
        Names.name(evaluator, "evaluator of condition " + name);
        options = Map.copyOf(requireNonNull(options, "options"));
    }

    /**
     * Creates a condition without options.
     *
     * @param name the name
     * @param label what the console shows
     * @param evaluator the evaluator's name
     * @return the condition
     */
    public static ConditionDefinition of(String name, String label, String evaluator)
    {
        return new ConditionDefinition(name, label, evaluator, Map.of());
    }
}
