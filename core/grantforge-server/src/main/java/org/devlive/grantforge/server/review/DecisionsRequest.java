// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A reviewer's decision about some assignments of a round.
 *
 * @param itemIds the assignments under review, 1 to 200
 * @param decision keep, revoke, or pending to take decisions back
 * @param comment why
 */
public record DecisionsRequest(
        @NotNull @Size(min = 1, max = 200) @Nullable List<@NotNull @Size(max = 20) String> itemIds,
        @NotNull @Nullable ReviewDecision decision,
        @Size(max = 500) @Nullable String comment)
{
    /** Copies the items; JSON without them gives an empty list, which validation refuses. */
    public DecisionsRequest
    {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }

    /**
     * Returns the items.
     *
     * @return their IDs
     */
    public List<Long> items()
    {
        return (itemIds == null ? List.<String>of() : itemIds).stream().map(id -> PathIds.parse(id.strip(), "item")).toList();
    }

    /**
     * Returns the decision.
     *
     * @return the decision; validation makes sure there is one
     */
    public ReviewDecision chosen()
    {
        return decision == null ? ReviewDecision.PENDING : decision;
    }
}
