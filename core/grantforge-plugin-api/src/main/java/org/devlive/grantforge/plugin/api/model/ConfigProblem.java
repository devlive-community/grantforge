// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Something wrong with one configuration value of a service.
 *
 * @param field the field's name
 * @param reason what is wrong
 * @param detail a plugin's explanation for {@link Reason#INVALID}, otherwise usually {@code null}
 */
public record ConfigProblem(String field, Reason reason, @Nullable String detail)
{
    /** Checks the values. */
    public ConfigProblem
    {
        requireNonNull(field, "field");
        requireNonNull(reason, "reason");
    }

    /**
     * Creates a problem without detail.
     *
     * @param field the field's name
     * @param reason what is wrong
     * @return the problem
     */
    public static ConfigProblem of(String field, Reason reason)
    {
        return new ConfigProblem(field, reason, null);
    }

    /** What is wrong with a value. */
    public enum Reason
    {
        /** A mandatory field without a value or default. */
        REQUIRED,

        /** A field the service type does not declare. */
        UNKNOWN_FIELD,

        /** Not a whole number. */
        NOT_AN_INTEGER,

        /** Neither {@code true} nor {@code false}. */
        NOT_A_BOOLEAN,

        /** Not one of the field's options. */
        NOT_AN_OPTION,

        /** Does not match the field's pattern. */
        PATTERN_MISMATCH,

        /** Refused by the plugin; see the detail. */
        INVALID
    }
}
