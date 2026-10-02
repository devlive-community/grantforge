// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.policy.PolicyDocument;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Everything an agent needs to decide requests for a service on its own: how the service type matches resources, the
 * enabled policies, and who holds the roles and belongs to the groups the policies name. Agents apply snapshots whole;
 * a snapshot has no timestamp, so the same policies always give the same bytes.
 *
 * @param format the snapshot format, raised on incompatible changes
 * @param service the service's name
 * @param serviceType the service type's name
 * @param typeVersion the version of the service type's definition
 * @param serviceEnabled whether the service is in use
 * @param policyVersion the service's policy version
 * @param definition how the service type matches resources
 * @param policies the enabled policies, by id
 * @param roles the user names holding each role the policies name, including through groups, departments, positions and
 *        roles inheriting from it
 * @param groups the user names in each group the policies name; agents add the groups their system knows
 */
public record PolicySnapshot(int format, String service, String serviceType, int typeVersion, boolean serviceEnabled, long policyVersion,
        Definition definition, List<SnapshotPolicy> policies, Map<String, List<String>> roles, Map<String, List<String>> groups)
{
    /** The current format. */
    public static final int FORMAT = 1;

    /** Copies the parts. */
    public PolicySnapshot
    {
        policies = List.copyOf(policies);
        roles = Map.copyOf(roles);
        groups = Map.copyOf(groups);
    }

    /**
     * How a service type matches resources and access types.
     *
     * @param resources the resource levels
     * @param accessTypes the access types with those they include
     * @param conditions the conditions with the evaluator agents use for each
     * @param maskTypes the masking methods
     */
    public record Definition(List<Level> resources, List<Access> accessTypes, List<Condition> conditions, List<Mask> maskTypes)
    {
        /** Copies the lists. */
        public Definition
        {
            resources = List.copyOf(resources);
            accessTypes = List.copyOf(accessTypes);
            conditions = List.copyOf(conditions);
            maskTypes = List.copyOf(maskTypes);
        }
    }

    /**
     * A resource level.
     *
     * @param name its name
     * @param parent the level above, or {@code null} for a top level
     * @param matcher how values are compared
     * @param caseSensitive whether values compare case-sensitively
     */
    public record Level(String name, @Nullable String parent, MatcherType matcher, boolean caseSensitive)
    {
    }

    /**
     * An access type.
     *
     * @param name its name
     * @param impliedGrants the access types it includes
     */
    public record Access(String name, List<String> impliedGrants)
    {
        /** Copies the implied grants. */
        public Access
        {
            impliedGrants = List.copyOf(impliedGrants);
        }
    }

    /**
     * A condition.
     *
     * @param name its name
     * @param evaluator the evaluator agents use
     * @param options the evaluator's options
     */
    public record Condition(String name, String evaluator, Map<String, String> options)
    {
        /** Copies the options. */
        public Condition
        {
            options = Map.copyOf(options);
        }
    }

    /**
     * A masking method.
     *
     * @param name its name
     * @param transformer the expression the target system applies, or {@code null} if items give one
     */
    public record Mask(String name, @Nullable String transformer)
    {
    }

    /**
     * An enabled policy.
     *
     * @param id its id, reported in decisions and audits
     * @param name its name
     * @param type what kind of policy it is
     * @param priority whether it overrides normal policies
     * @param document what it covers and says
     */
    public record SnapshotPolicy(long id, String name, PolicyType type, PolicyPriority priority, PolicyDocument document)
    {
    }
}
