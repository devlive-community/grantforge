// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import java.io.Serial;
import java.util.List;

/** A service type definition whose parts do not fit together; lists every problem found. */
public final class DefinitionException
        extends IllegalArgumentException
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** The problems; an immutable list. */
    @SuppressWarnings("serial")
    private final List<String> problems;

    /**
     * Creates the exception.
     *
     * @param serviceType the definition's name
     * @param problems what is wrong, at least one
     */
    public DefinitionException(String serviceType, List<String> problems)
    {
        super("invalid service type " + serviceType + ": " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * Returns every problem found.
     *
     * @return the problems
     */
    public List<String> getProblems()
    {
        return problems;
    }
}
