// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.AccessCheck;
import org.devlive.grantforge.authz.application.AccessKind;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A question whether an account may use something.
 *
 * @param kind a console resource or an API permission
 * @param code its code, such as {@code system.user.btn.edit} or {@code system.user.update}
 */
public record AccessCheckRequest(@NotNull @Nullable AccessKind kind, @NotBlank @Size(max = 255) @Nullable String code)
{
    /**
     * Converts the validated request.
     *
     * @return the question
     */
    public AccessCheck toCheck()
    {
        return new AccessCheck(requireNonNull(kind, "kind"), requireNonNull(code, "code").strip());
    }
}
