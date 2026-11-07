// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The access types of the MongoDB service and the actions of MongoDB each one is. Every action named here exists in all the
 * releases the agent supports: {@code createRole} refuses a role with an action its server does not know.
 */
final class Actions
{
    static final String READ = "read";
    static final String CHANGE_STREAM = "change_stream";
    static final String INSERT = "insert";
    static final String UPDATE = "update";
    static final String REMOVE = "remove";
    static final String CREATE_INDEX = "create_index";
    static final String DROP_INDEX = "drop_index";
    static final String ALTER = "alter";
    static final String BYPASS_VALIDATION = "bypass_validation";
    static final String DROP = "drop";
    static final String CREATE = "create";
    static final String DROP_DATABASE = "drop_database";
    static final String LIST_DATABASES = "list_databases";
    static final String MONITOR = "monitor";

    /** The access types decided by collection, in the order they are planned. */
    static final List<String> COLLECTION_TYPES = List.of(READ, CHANGE_STREAM, INSERT, UPDATE, REMOVE, CREATE_INDEX, DROP_INDEX, ALTER, BYPASS_VALIDATION, DROP);

    /** The access types decided by the deployment as a whole. */
    static final List<String> DEPLOYMENT_TYPES = List.of(LIST_DATABASES, MONITOR);

    /** What reading a whole database gives besides reading each collection of it: nothing it holds stays unseen, so its names and sizes may show. */
    static final List<String> WHOLE_DATABASE_READ = List.of("listCollections", "dbStats");

    /** The actions that let a user change who may do what, so that a user who has them can give itself anything. */
    static final Set<String> USER_ADMINISTRATION = Set.of("createUser", "dropUser", "grantRole", "revokeRole", "createRole", "dropRole", "changeCustomData");

    private static final Map<String, List<String>> BY_TYPE = byType();

    private Actions()
    {
    }

    private static Map<String, List<String>> byType()
    {
        Map<String, List<String>> byType = new LinkedHashMap<>();
        byType.put(READ, List.of("find", "listIndexes", "collStats", "killCursors"));
        byType.put(CHANGE_STREAM, List.of("changeStream"));
        byType.put(INSERT, List.of("insert"));
        byType.put(UPDATE, List.of("update"));
        byType.put(REMOVE, List.of("remove"));
        byType.put(CREATE_INDEX, List.of("createIndex"));
        byType.put(DROP_INDEX, List.of("dropIndex"));
        byType.put(ALTER, List.of("collMod", "validate", "convertToCapped"));
        byType.put(BYPASS_VALIDATION, List.of("bypassDocumentValidation"));
        byType.put(DROP, List.of("dropCollection"));
        byType.put(CREATE, List.of("createCollection"));
        byType.put(DROP_DATABASE, List.of("dropDatabase"));
        byType.put(LIST_DATABASES, List.of("listDatabases"));
        byType.put(MONITOR, List.of("serverStatus", "top", "replSetGetStatus", "connPoolStats", "hostInfo", "inprog"));
        return Map.copyOf(byType);
    }

    /**
     * Returns the actions of an access type.
     *
     * @param type the access type
     * @return its actions
     * @throws IllegalArgumentException for an access type the service does not have
     */
    static List<String> of(String type)
    {
        List<String> actions = BY_TYPE.get(type);
        if (actions == null) {
            throw new IllegalArgumentException("no access type " + type);
        }
        return actions;
    }

    /**
     * Names the access type that decides whether an action may be used, for what roles outside GrantForge give. Reading
     * is named by {@code find}, as the rest of what it holds does not read documents.
     *
     * @param action the action
     * @return the access type, or {@code null} for an action no access type decides
     */
    static @Nullable String type(String action)
    {
        if ("find".equals(action)) {
            return READ;
        }
        for (Map.Entry<String, List<String>> entry : BY_TYPE.entrySet()) {
            if (!READ.equals(entry.getKey()) && entry.getValue().contains(action)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
