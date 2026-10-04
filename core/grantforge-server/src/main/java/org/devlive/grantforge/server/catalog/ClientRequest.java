// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.devlive.grantforge.authz.domain.ClientType;
import org.jspecify.annotations.Nullable;

/**
 * A new OAuth client.
 *
 * @param type confidential (a server that keeps a secret) or public (a browser or mobile app)
 * @param settings what it may do
 */
public record ClientRequest(@NotNull @Nullable ClientType type, @NotNull @Valid @Nullable ClientSettingsRequest settings)
{
}
