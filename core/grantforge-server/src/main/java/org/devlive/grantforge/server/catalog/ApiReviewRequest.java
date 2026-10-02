// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.server.web.PathIds;

import java.util.List;

/**
 * The endpoints whose changes an administrator confirms.
 *
 * @param endpointIds endpoint IDs, at most 1000; may be empty
 */
public record ApiReviewRequest(@NotNull @Size(max = 1000) List<@NotNull @Size(max = 20) String> endpointIds)
{
    /** Copies the IDs; JSON without them gives an empty list. */
    @SuppressWarnings("ConstantValue")
    public ApiReviewRequest
    {
        endpointIds = endpointIds == null ? List.of() : List.copyOf(endpointIds);
    }

    /**
     * Parses the IDs.
     *
     * @return the endpoint IDs
     */
    public List<Long> ids()
    {
        return endpointIds.stream().map(id -> PathIds.parse(id.trim(), "endpoint")).toList();
    }
}
