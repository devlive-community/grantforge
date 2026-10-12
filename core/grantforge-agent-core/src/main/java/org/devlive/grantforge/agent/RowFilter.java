// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import static java.util.Objects.requireNonNull;

/** The rows a row filtering policy lets a user see: a condition in the target system's own language. Immutable. */
public final class RowFilter
{
    private final long policyVersion;
    private final long policyId;
    private final String expression;

    RowFilter(long policyVersion, long policyId, String expression)
    {
        this.policyVersion = policyVersion;
        this.policyId = policyId;
        this.expression = requireNonNull(expression, "expression");
    }

    /**
     * Returns the policy version of the snapshot that decided.
     *
     * @return the version
     */
    public long policyVersion()
    {
        return policyVersion;
    }

    /**
     * Returns the row filtering policy that applies.
     *
     * @return its id
     */
    public long policyId()
    {
        return policyId;
    }

    /**
     * Returns the condition rows must meet.
     *
     * @return the condition, such as {@code region = 'EU'}
     */
    public String expression()
    {
        return expression;
    }

    @Override
    public String toString()
    {
        return expression + " by policy " + policyId;
    }
}
