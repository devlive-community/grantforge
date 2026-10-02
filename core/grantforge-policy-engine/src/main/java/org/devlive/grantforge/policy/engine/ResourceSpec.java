// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A policy's values for one resource level: the resource matches if its value matches one of them, or, with
 * {@link #excludes()}, none of them; {@code *} matches every value. A recursive path value also covers everything
 * below it.
 */
public final class ResourceSpec
{
    /** The value that matches every value. */
    public static final String ANY = "*";

    private final List<String> values;
    private final boolean excludes;
    private final boolean recursive;

    private ResourceSpec(Collection<String> values, boolean excludes, boolean recursive)
    {
        this.values = Checks.list(values, "resource values");
        for (String value : this.values) {
            Checks.text(value, "resource value");
        }
        if (this.values.isEmpty()) {
            throw new IllegalArgumentException("a resource needs at least one value");
        }
        this.excludes = excludes;
        this.recursive = recursive;
    }

    /**
     * Matches any of the values.
     *
     * @param values the values
     * @return the spec
     */
    public static ResourceSpec of(String... values)
    {
        return new ResourceSpec(Arrays.asList(values), false, false);
    }

    /**
     * Matches any of the values.
     *
     * @param values the values; at least one
     * @param excludes whether to match every value except these instead
     * @param recursive whether a path value also covers everything below it
     * @return the spec
     */
    public static ResourceSpec of(Collection<String> values, boolean excludes, boolean recursive)
    {
        return new ResourceSpec(values, excludes, recursive);
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public List<String> values()
    {
        return values;
    }

    /**
     * Returns whether every value except these matches.
     *
     * @return {@code true} for an exclusion
     */
    public boolean excludes()
    {
        return excludes;
    }

    /**
     * Returns whether a path value also covers everything below it.
     *
     * @return {@code true} if recursive
     */
    public boolean recursive()
    {
        return recursive;
    }

    /**
     * Returns whether the spec matches every value.
     *
     * @return {@code true} for {@code *} without exclusion
     */
    public boolean matchesAnything()
    {
        return !excludes && values.contains(ANY);
    }
}
