// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A console session with its owner's names, as listed to administrators.
 *
 * @param session the session
 * @param username the owner's login name
 * @param displayName the owner's display name, if any
 */
public record ConsoleSessionEntry(ConsoleSession session, String username, @Nullable String displayName)
{
    /** Validates the values. */
    public ConsoleSessionEntry
    {
        requireNonNull(session, "session");
        requireNonNull(username, "username");
    }
}
