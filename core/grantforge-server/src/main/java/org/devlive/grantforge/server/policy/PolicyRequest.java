// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.policy.PolicyCommand;
import org.devlive.grantforge.service.policy.PolicyDocument;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A policy to add or change; the kind is only given when adding.
 *
 * @param type what kind of policy it is; access unless said otherwise, ignored when changing a policy
 * @param name its name, unique within the service
 * @param description a longer explanation
 * @param priority whether it overrides normal policies; normal unless said otherwise
 * @param enabled whether it is in use; in use unless said otherwise
 * @param labels labels to find it by
 * @param document what it covers and says
 * @param version the version the change was made on, so a concurrent change is not overwritten; any when left out
 */
public record PolicyRequest(
        @Nullable PolicyType type,
        @NotNull @Size(max = 128) @Nullable String name,
        @Size(max = 512) @Nullable String description,
        @Nullable PolicyPriority priority,
        @Nullable Boolean enabled,
        @Size(max = 20) List<@Size(max = 64) String> labels,
        @NotNull @Nullable PolicyDocument document,
        @Nullable Long version)
{
    /** Copies the labels; JSON without them, or with labels set to {@code null}, leaves them out. */
    @SuppressWarnings("ConstantValue")
    public PolicyRequest
    {
        labels = labels == null ? List.of() : List.copyOf(labels.stream().filter(Objects::nonNull).toList());
    }

    /**
     * Returns the kind of policy to add.
     *
     * @return the kind
     */
    public PolicyType kind()
    {
        return type == null ? PolicyType.ACCESS : type;
    }

    /**
     * Turns the request into a command.
     *
     * @return the command
     */
    public PolicyCommand command()
    {
        return new PolicyCommand(String.valueOf(name), description, priority == null ? PolicyPriority.NORMAL : priority,
                enabled == null || enabled, labels, document == null ? PolicyDocument.allowing(Map.of()) : document);
    }
}
