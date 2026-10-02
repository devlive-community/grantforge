// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import java.util.List;

/**
 * A condition of a policy item, such as the client addresses it applies to.
 *
 * @param type a condition the service type defines
 * @param values its values, stripped, without blanks or repeats
 */
public record ConditionValues(String type, List<String> values)
{
    /** Tidies the values. */
    @SuppressWarnings("ConstantValue")
    public ConditionValues
    {
        type = type == null ? "" : type.strip();
        values = List.copyOf(Texts.of(values));
    }
}
