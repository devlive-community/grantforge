// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Changes to the signed-in user's own profile; blank values clear a field.
 *
 * @param displayName the display name
 * @param email the e-mail address
 */
public record ProfileRequest(@Size(max = 128) @Nullable String displayName, @Size(max = 254) @Nullable String email)
{
}
