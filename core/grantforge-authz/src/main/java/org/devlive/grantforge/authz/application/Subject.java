// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.SubjectType;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Someone or something a role can be given to, as the console shows it.
 *
 * @param type what it is
 * @param id its ID
 * @param name its display name
 * @param detail its login name or code, or {@code null}
 */
public record Subject(SubjectType type, long id, String name, @Nullable String detail)
{
    /** Validates the values. */
    public Subject
    {
        requireNonNull(type, "type");
        requireNonNull(name, "name");
    }
}
