// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import static java.util.Objects.requireNonNull;

/**
 * The answer to an {@link AccessCheck}.
 *
 * @param kind a console resource or an API permission
 * @param code its code
 * @param allowed whether the account may use it now
 */
public record AccessResult(AccessKind kind, String code, boolean allowed)
{
    /** Checks that the parts are present. */
    public AccessResult
    {
        requireNonNull(kind, "kind");
        requireNonNull(code, "code");
    }
}
