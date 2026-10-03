// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/** How a masked field hides most of its value. */
public enum MaskStrategy
{
    /** An e-mail address keeps the first letter of the name and the domain: {@code a***@example.com}. */
    EMAIL
    {
        @Override
        String hide(String value)
        {
            int at = value.lastIndexOf('@');
            return at <= 0 ? PARTIAL.hide(value) : value.charAt(0) + STARS + value.substring(at);
        }
    },

    /** A phone number keeps its first three and last four characters: {@code 138****5678}. */
    PHONE
    {
        @Override
        String hide(String value)
        {
            return keep(value, 3, 4);
        }
    },

    /** An identity document number keeps its first six and last four characters. */
    ID_NUMBER
    {
        @Override
        String hide(String value)
        {
            return keep(value, 6, 4);
        }
    },

    /** Anything else keeps its first and last character: {@code A***e}. */
    PARTIAL
    {
        @Override
        String hide(String value)
        {
            return keep(value, 1, 1);
        }
    },

    /** Nothing of the value is kept. */
    FULL
    {
        @Override
        String hide(String value)
        {
            return STARS;
        }
    };

    private static final String STARS = "***";

    /**
     * Masks a value.
     *
     * @param value the value
     * @return the masked value; values too short to keep anything become {@code ***}
     */
    public String mask(String value)
    {
        return requireNonNull(value, "value").isEmpty() ? "" : hide(value);
    }

    abstract String hide(String value);

    /** Keeps the start and the end of a value, or nothing if the value is not longer than what would be kept. */
    private static String keep(String value, int head, int tail)
    {
        if (value.codePointCount(0, value.length()) <= head + tail) {
            return STARS;
        }
        int start = value.offsetByCodePoints(0, head);
        int end = value.offsetByCodePoints(value.length(), -tail);
        return value.substring(0, start) + STARS + value.substring(end);
    }

    /**
     * Reads a strategy's name, ignoring case.
     *
     * @param name the name, such as {@code email}
     * @return the strategy
     * @throws IllegalArgumentException if no strategy has the name
     */
    public static MaskStrategy of(String name)
    {
        return valueOf(requireNonNull(name, "name").toUpperCase(Locale.ROOT));
    }
}
