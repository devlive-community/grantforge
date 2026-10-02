// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A policy as stored.
 *
 * @param id the policy's id
 * @param serviceId the service it belongs to
 * @param name its name
 * @param description a longer explanation
 * @param type what kind of policy it is
 * @param priority whether it overrides normal policies
 * @param enabled whether it is in use
 * @param labels its labels
 * @param document what it covers and says
 * @param version how often it was changed, to detect concurrent changes
 * @param updatedAt when it was last changed
 */
public record PolicyView(long id, long serviceId, String name, @Nullable String description, PolicyType type, PolicyPriority priority,
        boolean enabled, List<String> labels, PolicyDocument document, long version, Instant updatedAt)
{
    /** Copies the labels. */
    public PolicyView
    {
        labels = List.copyOf(labels);
    }
}
