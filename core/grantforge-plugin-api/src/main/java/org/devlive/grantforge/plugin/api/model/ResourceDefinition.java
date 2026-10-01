// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * One level of a service type's resource hierarchy, such as Hive's {@code database}, {@code table} or
 * {@code column}. Levels form trees through {@code parent}; a policy names values for a chain of levels starting
 * at a root.
 *
 * @param name the name, unique within the service type
 * @param label what the console shows
 * @param parent the level above, or {@code null} for a root
 * @param matcher how policy values are compared with requested resources
 * @param caseSensitive whether values compare case-sensitively
 * @param mandatory whether a policy that reaches this level must give at least one value
 * @param excludesSupported whether a policy may say "everything except these values"
 * @param recursiveSupported whether a value may also cover everything below it; only for {@link MatcherType#PATH}
 * @param lookupSupported whether the plugin can list existing values ({@code ServiceTypeProvider#lookup})
 * @param validLeaf whether a policy may end at this level although it has levels below; levels without children
 *        are always valid ends
 * @param accessTypes the access types that apply at this level; empty means all of the service type's
 */
public record ResourceDefinition(String name, String label, @Nullable String parent, MatcherType matcher,
        boolean caseSensitive, boolean mandatory, boolean excludesSupported, boolean recursiveSupported,
        boolean lookupSupported, boolean validLeaf, Set<String> accessTypes)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if a name is malformed, the label blank, the level its own parent, or
     *         recursion is enabled for a matcher other than {@link MatcherType#PATH}
     */
    public ResourceDefinition
    {
        Names.name(name, "resource name");
        label = Names.label(label, "label of resource " + name);
        if (parent != null) {
            Names.name(parent, "parent of resource " + name);
            if (parent.equals(name)) {
                throw new IllegalArgumentException("resource " + name + " is its own parent");
            }
        }
        requireNonNull(matcher, "matcher");
        if (recursiveSupported && matcher != MatcherType.PATH) {
            throw new IllegalArgumentException("resource " + name + " can only be recursive with the PATH matcher");
        }
        accessTypes = Set.copyOf(requireNonNull(accessTypes, "accessTypes"));
        for (String accessType : accessTypes) {
            Names.name(accessType, "access type of resource " + name);
        }
    }

    /**
     * Starts a level with defaults: label equal to the name, root, {@link MatcherType#WILDCARD}, case-sensitive,
     * mandatory, excludes supported, not recursive, no lookup, all access types.
     *
     * @param name the name
     * @return a builder
     */
    public static Builder builder(String name)
    {
        return new Builder(name);
    }

    /** Builds a {@link ResourceDefinition}. */
    public static final class Builder
    {
        private final String name;
        private String label;
        private @Nullable String parent;
        private MatcherType matcher = MatcherType.WILDCARD;
        private boolean caseSensitive = true;
        private boolean mandatory = true;
        private boolean excludesSupported = true;
        private boolean recursiveSupported;
        private boolean lookupSupported;
        private boolean validLeaf;
        private final Set<String> accessTypes = new HashSet<>();

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
         * Places the level below another.
         *
         * @param value the parent's name
         * @return this builder
         */
        public Builder parent(String value)
        {
            parent = value;
            return this;
        }

        /**
         * Sets the matcher.
         *
         * @param value the matcher
         * @return this builder
         */
        public Builder matcher(MatcherType value)
        {
            matcher = value;
            return this;
        }

        /**
         * Sets whether values compare case-sensitively.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder caseSensitive(boolean value)
        {
            caseSensitive = value;
            return this;
        }

        /**
         * Sets whether a policy must give a value at this level.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder mandatory(boolean value)
        {
            mandatory = value;
            return this;
        }

        /**
         * Sets whether excludes are supported.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder excludesSupported(boolean value)
        {
            excludesSupported = value;
            return this;
        }

        /**
         * Sets whether values may be recursive.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder recursiveSupported(boolean value)
        {
            recursiveSupported = value;
            return this;
        }

        /**
         * Sets whether the plugin can list values.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder lookupSupported(boolean value)
        {
            lookupSupported = value;
            return this;
        }

        /**
         * Sets whether a policy may end here although the level has children.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder validLeaf(boolean value)
        {
            validLeaf = value;
            return this;
        }

        /**
         * Restricts the access types that apply at this level.
         *
         * @param names access type names
         * @return this builder
         */
        public Builder accessTypes(String... names)
        {
            accessTypes.addAll(Set.of(names));
            return this;
        }

        /**
         * Builds the level.
         *
         * @return the level
         * @throws IllegalArgumentException if a value is invalid
         */
        public ResourceDefinition build()
        {
            return new ResourceDefinition(name, label, parent, matcher, caseSensitive, mandatory, excludesSupported,
                    recursiveSupported, lookupSupported, validLeaf, accessTypes);
        }
    }
}
