// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/** Checks the names and labels used throughout a definition. */
final class Names
{
    /** Names of resources, access types, mask types, conditions and capabilities. */
    static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private Names()
    {
    }

    static String name(@Nullable String value, String what)
    {
        if (value == null || !NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be 1-64 lowercase letters, digits, '_' or '-' starting with"
                    + " a letter: " + value);
        }
        return value;
    }

    static String label(@Nullable String value, String what)
    {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value.strip();
    }

    static @Nullable String optional(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
