// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.service.agent.AccessAudit;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A batch of access events from an agent.
 *
 * @param instance the name the agent gives itself, as in its heartbeats
 * @param events the events, at most {@value AccessAudit#MAX_BATCH}
 */
public record AccessEventBatch(
        @NotBlank @Size(max = 128) @Nullable String instance,
        @NotNull @Size(max = AccessAudit.MAX_BATCH) List<@Valid @NotNull AccessEventRequest> events)
{
    /** Copies the events; JSON without them gives none. */
    @SuppressWarnings("ConstantValue")
    public AccessEventBatch
    {
        events = events == null ? List.of() : List.copyOf(events.stream().filter(Objects::nonNull).toList());
    }
}
