// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.AuthorizationInsight;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Questions whether an account may use some resources and permissions.
 *
 * @param accountId the account asked about; the signed-in user if absent. A string, as IDs exceed JavaScript's safe integers
 * @param checks the questions
 */
public record AccessChecksRequest(@Pattern(regexp = "\\d{1,19}") @Nullable String accountId,
        @Size(min = 1, max = AuthorizationInsight.MAX_CHECKS) List<@Valid AccessCheckRequest> checks)
{
    /** Copies the list; JSON without it gives {@code null}, which means none. */
    @SuppressWarnings("ConstantValue")
    public AccessChecksRequest
    {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
