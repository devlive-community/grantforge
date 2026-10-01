// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.jspecify.annotations.Nullable;

/**
 * One way of masking a value, such as showing only its last four characters.
 *
 * @param name the name, unique within the service type
 * @param label what the console shows
 * @param transformer how the target system applies it, for example {@code mask_show_last_n({col}, 4)}; only the
 *        target system interprets it, the server never runs it. {@code null} when the agent knows the mask by name
 */
public record MaskTypeDefinition(String name, String label, @Nullable String transformer)
{
    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException if the name is malformed or the label blank
     */
    public MaskTypeDefinition
    {
        Names.name(name, "mask type name");
        label = Names.label(label, "label of mask type " + name);
        transformer = Names.optional(transformer);
    }
}
