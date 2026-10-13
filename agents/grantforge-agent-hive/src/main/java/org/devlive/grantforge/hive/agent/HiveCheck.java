// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/** One access a statement needs: a resource of the Hive service type, from its root down, and an access type. Immutable. */
final class HiveCheck
{
    private final Map<String, String> resource;
    private final String accessType;
    private final String level;

    /**
     * Creates a check.
     *
     * @param accessType the access type, or {@link AccessRequest#ANY_ACCESS}
     * @param levels the levels and their values, from the root down, in pairs
     * @throws IllegalArgumentException when a value is missing, which Hive names for every object the agent checks
     */
    HiveCheck(String accessType, String... levels)
    {
        if (levels.length == 0 || levels.length % 2 != 0) {
            throw new IllegalArgumentException("a check names levels and their values");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < levels.length; index += 2) {
            String value = levels[index + 1];
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Hive names no " + levels[index]);
            }
            values.put(levels[index], value);
        }
        this.resource = Collections.unmodifiableMap(values);
        this.accessType = requireNonNull(accessType, "accessType");
        this.level = levels[levels.length - 2];
    }

    /**
     * Returns the resource.
     *
     * @return the values by level, from the root down
     */
    Map<String, String> resource()
    {
        return resource;
    }

    /**
     * Returns the access type.
     *
     * @return such as {@code select}, or {@link AccessRequest#ANY_ACCESS}
     */
    String accessType()
    {
        return accessType;
    }

    /**
     * Returns the lowest level.
     *
     * @return such as {@code column}
     */
    String level()
    {
        return level;
    }

    /**
     * Returns the resource as Hive users write it, for messages and audit events.
     *
     * @return such as {@code sales.orders.card} or a URL
     */
    String display()
    {
        return String.join(".", resource.values());
    }

    /**
     * Returns the table of a check on a table or one of its columns.
     *
     * @return the database and the table; empty for other levels
     */
    List<String> table()
    {
        if (!resource.containsKey("table")) {
            return Collections.emptyList();
        }
        List<String> table = new ArrayList<>(2);
        table.add(resource.get("database"));
        table.add(resource.get("table"));
        return table;
    }

    /**
     * Builds the request of a user.
     *
     * @param user the user
     * @param groups the user's groups, as Hive knows them
     * @param clientIp the client's address, or {@code null}
     * @return the request
     */
    AccessRequest request(String user, List<String> groups, @Nullable String clientIp)
    {
        return builder(user, groups, clientIp).build();
    }

    /**
     * Starts the request of a user.
     *
     * @param user the user
     * @param groups the user's groups, as Hive knows them
     * @param clientIp the client's address, or {@code null}
     * @return the request's builder
     */
    AccessRequest.Builder builder(String user, List<String> groups, @Nullable String clientIp)
    {
        AccessRequest.Builder request = AccessRequest.builder(user, accessType).groups(groups.toArray(new String[0]));
        for (Map.Entry<String, String> value : resource.entrySet()) {
            request.resource(value.getKey(), value.getValue());
        }
        if (clientIp != null) {
            request.context(Collections.<String, Object>singletonMap("clientAddress", clientIp));
        }
        return request;
    }

    @Override
    public boolean equals(@Nullable Object other)
    {
        return other instanceof HiveCheck && ((HiveCheck) other).accessType.equals(accessType) && ((HiveCheck) other).resource.equals(resource);
    }

    @Override
    public int hashCode()
    {
        return 31 * resource.hashCode() + accessType.hashCode();
    }

    @Override
    public String toString()
    {
        return accessType + " on " + display();
    }
}
