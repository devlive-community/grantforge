// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import static java.util.Objects.requireNonNull;

/**
 * Published after an account, group, department or position of the bound tenant was deleted (its transaction
 * committed), so other modules can drop what refers to it, such as role assignments.
 *
 * @param kind what was deleted
 * @param id its ID
 */
public record IdentityDeleted(Kind kind, long id)
{
    /** Checks the kind. */
    public IdentityDeleted
    {
        requireNonNull(kind, "kind");
    }

    /** What can be deleted. */
    public enum Kind
    {
        /** A user account. */
        ACCOUNT,

        /** A user group. */
        GROUP,

        /** A department. */
        ORG_UNIT,

        /** A position. */
        POSITION
    }
}
