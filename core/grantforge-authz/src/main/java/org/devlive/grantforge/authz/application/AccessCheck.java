// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import static java.util.Objects.requireNonNull;

/**
 * A question whether someone may use something.
 *
 * @param kind a console resource or an API permission
 * @param code its code
 */
public record AccessCheck(AccessKind kind, String code)
{
    /** Checks that both parts are present. */
    public AccessCheck
    {
        requireNonNull(kind, "kind");
        requireNonNull(code, "code");
    }
}
