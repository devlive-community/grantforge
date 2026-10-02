// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.service.domain.PolicyPriority;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A policy to add or change.
 *
 * @param name its name, unique within the service
 * @param description a longer explanation, or {@code null}
 * @param priority whether it overrides normal policies
 * @param enabled whether it is in use
 * @param labels labels to find it by, stripped, without blanks or repeats
 * @param document what it covers and says
 */
public record PolicyCommand(String name, @Nullable String description, PolicyPriority priority, boolean enabled, List<String> labels,
        PolicyDocument document)
{
    /** Tidies the values. */
    public PolicyCommand
    {
        name = requireNonNull(name, "name").strip();
        description = Texts.optional(description);
        requireNonNull(priority, "priority");
        labels = List.copyOf(Texts.of(labels));
        requireNonNull(document, "document");
    }
}
