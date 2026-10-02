// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import java.util.List;

/**
 * What a policy covers at one resource level: the values (such as database names; {@code *} for all), or everything
 * except them, and for path levels whether each value also covers what is below it.
 *
 * @param values the values, stripped, without blanks or repeats
 * @param excludes whether the policy covers every value except these
 * @param recursive whether a value also covers everything below it
 */
public record ResourceValues(List<String> values, boolean excludes, boolean recursive)
{
    /** Tidies the values. */
    public ResourceValues
    {
        values = List.copyOf(Texts.of(values));
    }

    /**
     * Covers values.
     *
     * @param values the values
     * @return what the policy covers at the level
     */
    public static ResourceValues of(String... values)
    {
        return new ResourceValues(List.of(values), false, false);
    }
}
