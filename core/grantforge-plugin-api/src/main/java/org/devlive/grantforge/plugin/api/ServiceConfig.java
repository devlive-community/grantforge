// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * The configuration of one service as a plugin sees it: checked against the definition's fields, defaults
 * applied and secrets decrypted. Plugins must not log secret values.
 *
 * @param serviceName the service's name, for messages
 * @param values the values by field name
 */
public record ServiceConfig(String serviceName, Map<String, String> values)
{
    /** Copies the values. */
    public ServiceConfig
    {
        requireNonNull(serviceName, "serviceName");
        values = Map.copyOf(requireNonNull(values, "values"));
    }

    /**
     * Returns a value.
     *
     * @param field the field's name
     * @return the value, or {@code null} when not set
     */
    public @Nullable String get(String field)
    {
        return values.get(field);
    }

    /**
     * Returns a value that must be set.
     *
     * @param field the field's name
     * @return the value
     * @throws IllegalStateException if it is not set
     */
    public String require(String field)
    {
        String value = values.get(field);
        if (value == null) {
            throw new IllegalStateException("service " + serviceName + " has no value for " + field);
        }
        return value;
    }

    /**
     * Returns a whole number.
     *
     * @param field the field's name
     * @param fallback the result when not set
     * @return the value or the fallback
     * @throws NumberFormatException if the value is not a number
     */
    public long getLong(String field, long fallback)
    {
        String value = values.get(field);
        return value == null ? fallback : Long.parseLong(value.startsWith("+") ? value.substring(1) : value);
    }

    /**
     * Returns a flag.
     *
     * @param field the field's name
     * @param fallback the result when not set
     * @return {@code true} only for the value {@code true}, or the fallback
     */
    public boolean getBoolean(String field, boolean fallback)
    {
        String value = values.get(field);
        return value == null ? fallback : "true".equals(value);
    }

    @Override
    public String toString()
    {
        // Values may be secrets.
        return "ServiceConfig[" + serviceName + ", fields=" + values.keySet().stream().sorted().toList() + "]";
    }
}
