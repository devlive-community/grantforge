// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

/**
 * The allow item that applies to a request, for policies whose items carry what to do rather than a yes or no, such as
 * masking and row filtering: see {@link PolicyEngine#select}.
 */
public final class ItemMatch
{
    private final long policyId;
    private final int item;

    ItemMatch(long policyId, int item)
    {
        this.policyId = policyId;
        this.item = item;
    }

    /**
     * Returns the policy whose item applies.
     *
     * @return its id
     */
    public long policyId()
    {
        return policyId;
    }

    /**
     * Returns the item that applies.
     *
     * @return its position among the policy's allow items, from 0
     */
    public int item()
    {
        return item;
    }

    @Override
    public boolean equals(@Nullable Object other)
    {
        if (!(other instanceof ItemMatch)) {
            return false;
        }
        ItemMatch that = (ItemMatch) other;
        return policyId == that.policyId && item == that.item;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(policyId) * 31 + item;
    }

    @Override
    public String toString()
    {
        return "item " + item + " of policy " + policyId;
    }
}
