// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.AccessKind;
import org.jspecify.annotations.Nullable;

/**
 * A question why an account may or may not use something.
 *
 * @param accountId the account asked about; the signed-in user if absent
 * @param kind a console resource or an API permission
 * @param code its code
 */
public record AccessExplainRequest(@Pattern(regexp = "\\d{1,19}") @Nullable String accountId, @NotNull @Nullable AccessKind kind,
        @NotBlank @Size(max = 255) @Nullable String code)
{
    /**
     * Returns the question part.
     *
     * @return the question
     */
    public AccessCheckRequest check()
    {
        return new AccessCheckRequest(kind, code);
    }
}
