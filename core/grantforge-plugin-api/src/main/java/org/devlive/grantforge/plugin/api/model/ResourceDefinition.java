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
 * @param browseSupported whether the plugin can list a directory's entries page by page
 *        ({@code ServiceTypeProvider#browse}); only for {@link MatcherType#PATH}; since 1.1.0
 */
public record ResourceDefinition(String name, String label, @Nullable String parent, MatcherType matcher,
        boolean caseSensitive, boolean mandatory, boolean excludesSupported, boolean recursiveSupported,
        boolean lookupSupported, boolean validLeaf, Set<String> accessTypes, boolean browseSupported)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if a name is malformed, the label blank, the level its own parent, or
     *         recursion or browsing is enabled for a matcher other than {@link MatcherType#PATH}
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
        if (browseSupported && matcher != MatcherType.PATH) {
            throw new IllegalArgumentException("resource " + name + " can only be browsed with the PATH matcher");
        }
        accessTypes = Set.copyOf(requireNonNull(accessTypes, "accessTypes"));
        for (String accessType : accessTypes) {
            Names.name(accessType, "access type of resource " + name);
        }
    }

    /**
     * Creates a level that cannot be browsed, as API 1.0 did.
     *
     * @param name the name
     * @param label what the console shows
     * @param parent the level above, or {@code null} for a root
     * @param matcher how policy values are compared
     * @param caseSensitive whether values compare case-sensitively
     * @param mandatory whether a policy reaching this level needs a value
     * @param excludesSupported whether "everything except" is allowed
     * @param recursiveSupported whether a value may cover everything below it
     * @param lookupSupported whether the plugin can list values
     * @param validLeaf whether a policy may end here
     * @param accessTypes the access types of this level
     * @deprecated since 1.1.0; use {@link #builder(String)}, which plugins built against 1.0 keep calling unchanged
     */
    @Deprecated(since = "1.1.0")
    public ResourceDefinition(String name, String label, @Nullable String parent, MatcherType matcher, boolean caseSensitive,
            boolean mandatory, boolean excludesSupported, boolean recursiveSupported, boolean lookupSupported, boolean validLeaf,
            Set<String> accessTypes)
    {
        this(name, label, parent, matcher, caseSensitive, mandatory, excludesSupported, recursiveSupported, lookupSupported,
                validLeaf, accessTypes, false);
    }

    /**
     * Starts a level with defaults: label equal to the name, root, {@link MatcherType#WILDCARD}, case-sensitive,
     * mandatory, excludes supported, not recursive, no lookup, no browsing, all access types.
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
        private boolean browseSupported;
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
         * Sets whether the plugin can list a directory's entries page by page; needs {@link MatcherType#PATH}.
         *
         * @param value the flag
         * @return this builder
         * @since 1.1.0
         */
        public Builder browseSupported(boolean value)
        {
            browseSupported = value;
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
                    recursiveSupported, lookupSupported, validLeaf, accessTypes, browseSupported);
        }
    }
}
