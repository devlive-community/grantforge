// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.policy.PolicyDocument;
import org.devlive.grantforge.service.policy.PolicyView;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A policy. IDs are strings because they exceed JavaScript's safe integers.
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
 * @param version its version, to send back with a change
 * @param updatedAt when it was last changed
 */
public record PolicyResponse(String id, String serviceId, String name, @Nullable String description, PolicyType type,
        PolicyPriority priority, boolean enabled, List<String> labels, PolicyDocument document, long version, Instant updatedAt)
{
    /** Copies the labels. */
    public PolicyResponse
    {
        labels = List.copyOf(labels);
    }

    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static PolicyResponse from(PolicyView view)
    {
        return new PolicyResponse(Long.toString(view.id()), Long.toString(view.serviceId()), view.name(), view.description(),
                view.type(), view.priority(), view.enabled(), view.labels(), view.document(), view.version(), view.updatedAt());
    }
}
