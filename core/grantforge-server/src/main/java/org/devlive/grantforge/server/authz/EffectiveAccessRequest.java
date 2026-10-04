// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Whose effective access to list.
 *
 * @param accountId the account; the signed-in user if absent. A string, as IDs exceed JavaScript's safe integers
 */
public record EffectiveAccessRequest(@Pattern(regexp = "\\d{1,19}") @Nullable String accountId)
{
}
