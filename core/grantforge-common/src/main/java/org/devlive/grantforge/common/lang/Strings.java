// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.lang;

import org.jspecify.annotations.Nullable;

/**
 * Null-safe string normalisation.
 *
 * <p>Blank strings are treated as absent everywhere in GrantForge. This is required for database
 * portability: Oracle stores {@code ''} as {@code NULL}, so persisting an empty string would read back
 * differently on Oracle than on other databases.
 */
public final class Strings
{
    private Strings()
    {
    }

    /**
     * Returns the value without leading and trailing whitespace, or {@code null} when nothing remains.
     *
     * @param value any string; may be {@code null}
     * @return the stripped value, or {@code null} if {@code value} is {@code null}, empty or blank
     */
    public static @Nullable String blankToNull(@Nullable String value)
    {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Returns the stripped value, rejecting {@code null} and blank input.
     *
     * @param value the value to check; may be {@code null}
     * @param name the parameter name used in the error message
     * @return the stripped, non-empty value
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank
     */
    public static String requireNonBlank(@Nullable String value, String name)
    {
        String stripped = blankToNull(value);
        if (stripped == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }
}
