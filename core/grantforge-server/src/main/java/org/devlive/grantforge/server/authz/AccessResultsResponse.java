// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessKind;
import org.devlive.grantforge.authz.application.AccessResult;

import java.util.List;

/**
 * Whether an account may use some resources and permissions.
 *
 * @param accountId the account
 * @param results the answers, in the order of the questions
 */
public record AccessResultsResponse(String accountId, List<Result> results)
{
    /** Copies the list. */
    public AccessResultsResponse
    {
        results = List.copyOf(results);
    }

    /**
     * Converts answers.
     *
     * @param accountId the account
     * @param results the answers
     * @return the response
     */
    public static AccessResultsResponse from(long accountId, List<AccessResult> results)
    {
        return new AccessResultsResponse(Long.toString(accountId),
                results.stream().map(result -> new Result(result.kind(), result.code(), result.allowed())).toList());
    }

    /**
     * One answer.
     *
     * @param kind a console resource or an API permission
     * @param code its code
     * @param allowed whether the account may use it now
     */
    public record Result(AccessKind kind, String code, boolean allowed)
    {
    }
}
