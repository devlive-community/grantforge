// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A user as an identity source describes them.
 *
 * @param id what the source calls the user, stable across renames
 * @param username the user name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param phone the telephone number, if any
 * @param picture the picture URL, if any
 */
public record DirectoryUser(String id, String username, @Nullable String displayName, @Nullable String email, @Nullable String phone,
        @Nullable String picture)
{
    /** Creates a user without a telephone number. */
    public DirectoryUser(String id, String username, @Nullable String displayName, @Nullable String email)
    {
        this(id, username, displayName, email, null, null);
    }

    /** Creates a user without a picture URL. */
    public DirectoryUser(String id, String username, @Nullable String displayName, @Nullable String email, @Nullable String phone)
    {
        this(id, username, displayName, email, phone, null);
    }

    /** Checks the identifiers. */
    public DirectoryUser
    {
        requireNonNull(id, "id");
        requireNonNull(username, "username");
    }
}
