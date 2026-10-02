// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

/** One level of a service type's resources, such as Hive's {@code database}, {@code table} and {@code column}. */
public final class ResourceLevel
{
    private final String name;
    private final @Nullable String parent;
    private final MatcherKind matcher;
    private final boolean caseSensitive;

    private ResourceLevel(String name, @Nullable String parent, MatcherKind matcher, boolean caseSensitive)
    {
        this.name = Checks.text(name, "level name");
        this.parent = parent;
        this.matcher = Checks.notNull(matcher, "matcher");
        this.caseSensitive = caseSensitive;
    }

    /**
     * Describes a level.
     *
     * @param name the level's name, unique within the service type
     * @param parent the level above, or {@code null} for a root
     * @param matcher how values are compared
     * @param caseSensitive whether values compare case-sensitively
     * @return the level
     */
    public static ResourceLevel of(String name, @Nullable String parent, MatcherKind matcher, boolean caseSensitive)
    {
        return new ResourceLevel(name, parent, matcher, caseSensitive);
    }

    /**
     * Returns the level's name.
     *
     * @return the name
     */
    public String name()
    {
        return name;
    }

    /**
     * Returns the level above.
     *
     * @return its name, or {@code null} for a root
     */
    public @Nullable String parent()
    {
        return parent;
    }

    /**
     * Returns how values are compared.
     *
     * @return the matcher
     */
    public MatcherKind matcher()
    {
        return matcher;
    }

    /**
     * Returns whether values compare case-sensitively.
     *
     * @return {@code true} if case matters
     */
    public boolean caseSensitive()
    {
        return caseSensitive;
    }
}
