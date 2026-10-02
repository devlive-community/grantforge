// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** A condition a policy item adds, such as the client addresses it applies to; evaluated by a {@link ConditionEvaluator}. */
public final class Condition
{
    private final String type;
    private final List<String> values;

    private Condition(String type, Collection<String> values)
    {
        this.type = Checks.text(type, "condition type");
        this.values = Checks.list(values, "condition values");
    }

    /**
     * Describes a condition.
     *
     * @param type the condition type, such as {@code ip-range}
     * @param values its values, such as address ranges
     * @return the condition
     */
    public static Condition of(String type, String... values)
    {
        return new Condition(type, Arrays.asList(values));
    }

    /**
     * Returns the condition type.
     *
     * @return the type
     */
    public String type()
    {
        return type;
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public List<String> values()
    {
        return values;
    }
}
