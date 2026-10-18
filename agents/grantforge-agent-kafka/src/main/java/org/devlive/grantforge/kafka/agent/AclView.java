// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.Policy;
import org.devlive.grantforge.policy.engine.PolicyItem;
import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The access policies of a snapshot as Kafka ACLs, so that {@code kafka-acls --list} and the Admin API show what
 * GrantForge enforces. A topic value without wildcards is a literal pattern, one that ends in its only {@code *} a
 * prefixed one, and {@code *} Kafka's wildcard. Users are {@code User:} principals, groups {@code Group:} and roles
 * {@code Role:} ones; the public group is {@code User:*}. What ACLs cannot express is left out: other wildcards,
 * excluded values, items with conditions, the items of a policy that has exceptions to them, and policies that do not
 * apply now.
 */
final class AclView
{
    /** Kafka's principal type of users. */
    static final String USER = "User:";

    private AclView()
    {
    }

    /**
     * Returns the ACLs that match a filter.
     *
     * @param snapshot the snapshot
     * @param filter the filter
     * @param now the moment whose policies apply
     * @return the ACLs, without duplicates
     */
    static List<AclBinding> of(Snapshot snapshot, AclBindingFilter filter, Instant now)
    {
        Set<AclBinding> bindings = new LinkedHashSet<>();
        for (Policy policy : snapshot.policies()) {
            if (!policy.appliesAt(now)) {
                continue;
            }
            // Every level of the Kafka plugin is a root, so a policy names one.
            for (Map.Entry<String, ResourceSpec> resource : policy.resources().entrySet()) {
                ResourceType type = KafkaAccess.resourceType(resource.getKey());
                if (type == null || resource.getValue().excludes()) {
                    continue;
                }
                for (String value : resource.getValue().values()) {
                    ResourcePattern pattern = pattern(type, value);
                    if (pattern == null) {
                        continue;
                    }
                    if (policy.allowExceptions().isEmpty()) {
                        add(bindings, filter, pattern, policy.allow(), AclPermissionType.ALLOW);
                    }
                    if (policy.denyExceptions().isEmpty()) {
                        add(bindings, filter, pattern, policy.deny(), AclPermissionType.DENY);
                    }
                }
            }
        }
        return new ArrayList<>(bindings);
    }

    /**
     * Returns the pattern of a resource value.
     *
     * @param type the resource type
     * @param value the policy's value
     * @return the pattern, or {@code null} when Kafka has none for it
     */
    static @Nullable ResourcePattern pattern(ResourceType type, String value)
    {
        if (ResourceSpec.ANY.equals(value)) {
            return new ResourcePattern(type, ResourcePattern.WILDCARD_RESOURCE, PatternType.LITERAL);
        }
        int star = value.indexOf('*');
        if (value.indexOf('?') >= 0 || star >= 0 && star != value.length() - 1) {
            return null;
        }
        return star < 0 ? new ResourcePattern(type, value, PatternType.LITERAL)
                : new ResourcePattern(type, value.substring(0, star), PatternType.PREFIXED);
    }

    // One binding per principal and access type.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static void add(Set<AclBinding> bindings, AclBindingFilter filter, ResourcePattern pattern, List<PolicyItem> items,
            AclPermissionType permission)
    {
        for (PolicyItem item : items) {
            if (!item.conditions().isEmpty()) {
                continue;
            }
            for (String principal : principals(item)) {
                for (String accessType : item.accessTypes()) {
                    AclOperation operation = KafkaAccess.operation(accessType);
                    if (operation == null) {
                        continue;
                    }
                    AclBinding binding = new AclBinding(pattern, new AccessControlEntry(principal, "*", operation, permission));
                    if (filter.matches(binding)) {
                        bindings.add(binding);
                    }
                }
            }
        }
    }

    private static List<String> principals(PolicyItem item)
    {
        List<String> principals = new ArrayList<>();
        for (String user : item.users()) {
            principals.add(USER + user);
        }
        for (String group : item.groups()) {
            principals.add(PolicyItem.PUBLIC.equals(group) ? USER + "*" : "Group:" + group);
        }
        for (String role : item.roles()) {
            principals.add("Role:" + role);
        }
        return principals;
    }
}
