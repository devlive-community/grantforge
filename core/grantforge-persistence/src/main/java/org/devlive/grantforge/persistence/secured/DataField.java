// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A field data permission conditions may test.
 *
 * @param code the attribute, such as {@code status}
 * @param name what it is, such as {@code Status}
 * @param type what kind of value it holds
 * @param choices the values of a {@link DataFieldType#CHOICE} field; empty otherwise
 */
public record DataField(String code, String name, DataFieldType type, List<String> choices)
{
    /** Checks and copies the values. */
    public DataField
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        choices = List.copyOf(choices);
    }
}
