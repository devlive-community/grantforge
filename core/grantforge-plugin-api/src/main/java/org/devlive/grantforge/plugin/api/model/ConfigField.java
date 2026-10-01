// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Objects.requireNonNull;

/**
 * One setting of a service of this type, such as a NameNode address or a Kerberos keytab. The console renders a
 * form from the fields; the server checks values with {@link #check(String)}.
 *
 * @param name the name, unique within the service type; letters, digits, dots, '_' and '-' (Hadoop style keys
 *        such as {@code fs.defaultFS} are allowed)
 * @param label what the console shows
 * @param type the kind of value
 * @param mandatory whether a service must set it (a default counts)
 * @param defaultValue the value used when none is given; never for {@link ConfigFieldType#SECRET}
 * @param options the allowed values of an {@link ConfigFieldType#ENUM}; empty for other types
 * @param pattern a regular expression a {@link ConfigFieldType#STRING} or {@link ConfigFieldType#TEXT} value must
 *        match as a whole, or {@code null}
 * @param description help text shown below the input, or {@code null}
 */
public record ConfigField(String name, String label, ConfigFieldType type, boolean mandatory,
        @Nullable String defaultValue, List<String> options, @Nullable String pattern, @Nullable String description)
{
    /** Configuration field names. */
    public static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d{1,18}");

    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if the name is malformed, the label blank, options are missing or given
     *         for the wrong type, the pattern does not compile or the default is not a valid value
     */
    public ConfigField
    {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("malformed config field name: " + name);
        }
        label = Names.label(label, "label of config field " + name);
        requireNonNull(type, "type");
        options = List.copyOf(requireNonNull(options, "options"));
        if (type == ConfigFieldType.ENUM == options.isEmpty()) {
            throw new IllegalArgumentException("config field " + name + ": options are required for ENUM fields only");
        }
        if (new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("config field " + name + " repeats an option");
        }
        pattern = Names.optional(pattern);
        if (pattern != null) {
            if (type != ConfigFieldType.STRING && type != ConfigFieldType.TEXT) {
                throw new IllegalArgumentException("config field " + name + ": a pattern needs a STRING or TEXT field");
            }
            try {
                Pattern.compile(pattern);
            }
            catch (PatternSyntaxException invalid) {
                throw new IllegalArgumentException("config field " + name + ": invalid pattern", invalid);
            }
        }
        description = Names.optional(description);
        if (defaultValue != null && type == ConfigFieldType.SECRET) {
            throw new IllegalArgumentException("secret config field " + name + " cannot have a default");
        }
        if (defaultValue != null && problem(type, options, pattern, defaultValue).isPresent()) {
            throw new IllegalArgumentException("config field " + name + ": invalid default " + defaultValue);
        }
    }

    /**
     * Starts a field with defaults: label equal to the name, {@link ConfigFieldType#STRING}, optional.
     *
     * @param name the name
     * @return a builder
     */
    public static Builder builder(String name)
    {
        return new Builder(name);
    }

    /**
     * Returns whether values must be stored encrypted and never shown.
     *
     * @return {@code true} for {@link ConfigFieldType#SECRET}
     */
    public boolean sensitive()
    {
        return type == ConfigFieldType.SECRET;
    }

    /**
     * Checks a value against the type, options and pattern (not against {@link #mandatory()}).
     *
     * @param value a non-blank value
     * @return what is wrong, or empty
     */
    public Optional<ConfigProblem.Reason> check(String value)
    {
        return problem(type, options, pattern, requireNonNull(value, "value"));
    }

    private static Optional<ConfigProblem.Reason> problem(ConfigFieldType type, List<String> options, @Nullable String pattern,
            String value)
    {
        ConfigProblem.Reason reason = switch (type) {
            case INTEGER -> INTEGER.matcher(value).matches() ? null : ConfigProblem.Reason.NOT_AN_INTEGER;
            case BOOLEAN -> "true".equals(value) || "false".equals(value) ? null : ConfigProblem.Reason.NOT_A_BOOLEAN;
            case ENUM -> options.contains(value) ? null : ConfigProblem.Reason.NOT_AN_OPTION;
            case STRING, TEXT -> pattern == null || Pattern.matches(pattern, value) ? null
                    : ConfigProblem.Reason.PATTERN_MISMATCH;
            case SECRET -> null;
        };
        return Optional.ofNullable(reason);
    }

    /** Builds a {@link ConfigField}. */
    public static final class Builder
    {
        private final String name;
        private String label;
        private ConfigFieldType type = ConfigFieldType.STRING;
        private boolean mandatory;
        private @Nullable String defaultValue;
        private final List<String> options = new ArrayList<>();
        private @Nullable String pattern;
        private @Nullable String description;

        private Builder(String name)
        {
            this.name = requireNonNull(name, "name");
            this.label = name;
        }

        /**
         * Sets what the console shows.
         *
         * @param value the label
         * @return this builder
         */
        public Builder label(String value)
        {
            label = value;
            return this;
        }

        /**
         * Sets the kind of value.
         *
         * @param value the type
         * @return this builder
         */
        public Builder type(ConfigFieldType value)
        {
            type = value;
            return this;
        }

        /**
         * Makes the field mandatory.
         *
         * @return this builder
         */
        public Builder mandatory()
        {
            mandatory = true;
            return this;
        }

        /**
         * Sets the default value.
         *
         * @param value the default
         * @return this builder
         */
        public Builder defaultValue(String value)
        {
            defaultValue = value;
            return this;
        }

        /**
         * Makes the field an {@link ConfigFieldType#ENUM} of these values.
         *
         * @param values the allowed values
         * @return this builder
         */
        public Builder options(String... values)
        {
            type = ConfigFieldType.ENUM;
            options.addAll(List.of(values));
            return this;
        }

        /**
         * Sets the pattern values must match.
         *
         * @param value a regular expression
         * @return this builder
         */
        public Builder pattern(String value)
        {
            pattern = value;
            return this;
        }

        /**
         * Sets the help text.
         *
         * @param value the text
         * @return this builder
         */
        public Builder description(String value)
        {
            description = value;
            return this;
        }

        /**
         * Builds the field.
         *
         * @return the field
         * @throws IllegalArgumentException if a value is invalid
         */
        public ConfigField build()
        {
            return new ConfigField(name, label, type, mandatory, defaultValue, options, pattern, description);
        }
    }
}
