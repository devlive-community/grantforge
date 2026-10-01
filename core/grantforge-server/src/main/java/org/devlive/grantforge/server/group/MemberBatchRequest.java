// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.identity.application.GroupService;
import org.devlive.grantforge.server.web.PathIds;

import java.util.List;

/**
 * Accounts to add to or remove from a group.
 *
 * @param accountIds the account IDs, 1 to {@value GroupService#MAX_BATCH}
 */
public record MemberBatchRequest(@NotEmpty @Size(max = GroupService.MAX_BATCH) List<String> accountIds)
{
    /** Copies the IDs; JSON without them gives {@code null}, which validation rejects. */
    @SuppressWarnings("ConstantValue")
    public MemberBatchRequest
    {
        accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
    }

    /**
     * Parses the IDs.
     *
     * @return the account IDs
     */
    public List<Long> ids()
    {
        return accountIds.stream().map(id -> PathIds.parse(id.trim(), "account")).toList();
    }
}
