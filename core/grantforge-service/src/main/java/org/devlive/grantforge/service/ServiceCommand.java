// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.jspecify.annotations.Nullable;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * What an administrator sets of a service.
 *
 * @param name the service's name: 2-64 lowercase letters, digits, '_' or '-', starting with a letter
 * @param label what the console shows
 * @param description a longer explanation, or {@code null}
 * @param enabled whether the service is in use
 * @param values the configuration by field; a blank secret keeps the stored one
 */
public record ServiceCommand(String name, String label, @Nullable String description, boolean enabled, Map<String, String> values)
{
    /** Copies the values. */
    public ServiceCommand
    {
        requireNonNull(name, "name");
        requireNonNull(label, "label");
        values = Map.copyOf(requireNonNull(values, "values"));
    }
}
