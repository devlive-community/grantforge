// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.resource.ResourceType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Kafka's resource types and ACL operations under the names the Kafka plugin gives them: the resource level is the
 * resource type's name and the access type the operation's, both in lower case, as {@code transactional_id} and
 * {@code describe_configs}.
 */
final class KafkaAccess
{
    /** The resource levels of the Kafka plugin. */
    static final Set<String> RESOURCES = names("topic", "group", "cluster", "transactional_id", "delegation_token", "user");

    /** The access types of the Kafka plugin. */
    static final Set<String> ACCESS_TYPES = names("read", "write", "create", "delete", "alter", "describe", "cluster_action",
            "describe_configs", "alter_configs", "idempotent_write", "create_tokens", "describe_tokens", "all");

    private KafkaAccess()
    {
    }

    private static Set<String> names(String... values)
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    /**
     * Returns the resource level of a resource type.
     *
     * @param type Kafka's resource type
     * @return the level, or {@code null} for a type the plugin does not know
     */
    static @Nullable String level(ResourceType type)
    {
        String name = type.name().toLowerCase(Locale.ROOT);
        return RESOURCES.contains(name) ? name : null;
    }

    /**
     * Returns the resource type of a resource level.
     *
     * @param level the level
     * @return Kafka's resource type, or {@code null} for a level that is none
     */
    static @Nullable ResourceType resourceType(String level)
    {
        return RESOURCES.contains(level) ? ResourceType.fromString(level) : null;
    }

    /**
     * Returns the access type of an operation.
     *
     * @param operation Kafka's ACL operation
     * @return the access type, or {@code null} for {@code ANY}, {@code UNKNOWN} or an operation the plugin does not know
     */
    static @Nullable String accessType(AclOperation operation)
    {
        String name = operation.name().toLowerCase(Locale.ROOT);
        return ACCESS_TYPES.contains(name) ? name : null;
    }

    /**
     * Returns the operation of an access type.
     *
     * @param accessType the access type
     * @return Kafka's ACL operation, or {@code null} for an access type that is none
     */
    static @Nullable AclOperation operation(String accessType)
    {
        return ACCESS_TYPES.contains(accessType) ? AclOperation.fromString(accessType) : null;
    }

    /**
     * Names a request type, such as {@code PRODUCE}.
     *
     * @param requestType the request's API key
     * @return its name, or {@code null} for a key this Kafka release does not know
     */
    static @Nullable String api(int requestType)
    {
        return requestType >= Short.MIN_VALUE && requestType <= Short.MAX_VALUE && ApiKeys.hasId(requestType) ? ApiKeys.forId(requestType).name()
                : null;
    }
}
