// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A token to issue.
 *
 * @param name what it is for, such as the cluster it is deployed to
 * @param expiresAt when it stops working; never when left out
 */
public record AgentTokenRequest(@NotNull @Size(max = 64) @Nullable String name, @Nullable Instant expiresAt)
{
}
