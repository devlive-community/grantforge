// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads what a command touched, as the audit log and the profiler both tell it: the command's name, its namespace and its
 * arguments. An aggregation also reads the collections its {@code $lookup}, {@code $graphLookup} and {@code $unionWith}
 * stages name, and writes those of {@code $out} and {@code $merge}, however deep they are nested; {@code bulkWrite}
 * names its own namespaces. Only the names of collections are taken from the arguments, as the arguments can hold
 * documents and passwords. What MongoDB keeps for itself and commands no access type decides are left out.
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
final class Accesses
{
    /** The commands by the access type that decides them. */
    private static final Map<String, String> TYPES = Map.ofEntries(Map.entry("find", Actions.READ), Map.entry("aggregate", Actions.READ), Map.entry("count", Actions.READ),
            Map.entry("distinct", Actions.READ), Map.entry("listIndexes", Actions.READ), Map.entry("insert", Actions.INSERT), Map.entry("update", Actions.UPDATE),
            Map.entry("delete", Actions.REMOVE), Map.entry("createIndexes", Actions.CREATE_INDEX), Map.entry("dropIndexes", Actions.DROP_INDEX),
            Map.entry("collMod", Actions.ALTER), Map.entry("validate", Actions.ALTER), Map.entry("convertToCapped", Actions.ALTER), Map.entry("drop", Actions.DROP));
    private static final Set<String> MONITORING = Set.of("serverStatus", "top", "replSetGetStatus", "connPoolStats", "hostInfo", "currentOp");
    private static final int MAX_DEPTH = 8;

    private Accesses()
    {
    }

    /**
     * One thing a command touched.
     *
     * @param type the access type that decides it
     * @param database the database, or {@code null} for the deployment as a whole
     * @param collection the collection, or {@code null} for a database or the deployment
     */
    record Access(String type, @Nullable String database, @Nullable String collection)
    {
    }

    /**
     * Reads what a command touched.
     *
     * @param command the command's name
     * @param namespace its namespace, such as {@code sales.orders}
     * @param arguments its arguments
     * @return what it touched, without repeats
     */
    static List<Access> of(String command, String namespace, Document arguments)
    {
        Set<Access> found = new LinkedHashSet<>();
        int dot = namespace.indexOf('.');
        String database = dot < 0 ? namespace : namespace.substring(0, dot);
        String collection = dot < 0 ? "" : namespace.substring(dot + 1);
        switch (command) {
            case "listDatabases" -> found.add(new Access(Actions.LIST_DATABASES, null, null));
            case "create" -> addDatabase(found, Actions.CREATE, database);
            case "dropDatabase" -> addDatabase(found, Actions.DROP_DATABASE, database);
            case "findAndModify", "findandmodify" -> add(found, Boolean.TRUE.equals(arguments.getBoolean("remove")) ? Actions.REMOVE : Actions.UPDATE, database, collection);
            case "bulkWrite" -> bulkWrite(arguments, found);
            default -> {
                if (MONITORING.contains(command)) {
                    found.add(new Access(Actions.MONITOR, null, null));
                }
                else if (TYPES.containsKey(command)) {
                    add(found, type(command, arguments), database, collection);
                }
            }
        }
        if ("aggregate".equals(command)) {
            pipeline(arguments.get("pipeline"), database, found, 0);
        }
        if (Boolean.TRUE.equals(arguments.getBoolean("bypassDocumentValidation")) && !found.isEmpty() && !"aggregate".equals(command)) {
            add(found, Actions.BYPASS_VALIDATION, database, collection);
        }
        return List.copyOf(found);
    }

    /** The access type of a command: an aggregation that opens with {@code $changeStream} is a change stream. */
    private static String type(String command, Document arguments)
    {
        if ("aggregate".equals(command) && arguments.get("pipeline") instanceof List<?> stages && !stages.isEmpty() && stages.get(0) instanceof Document first
                && first.containsKey("$changeStream")) {
            return Actions.CHANGE_STREAM;
        }
        return TYPES.getOrDefault(command, Actions.READ);
    }

    private static void bulkWrite(Document arguments, Set<Access> found)
    {
        List<Document> namespaces = arguments.getList("nsInfo", Document.class);
        List<Document> operations = arguments.getList("ops", Document.class);
        if (namespaces == null || operations == null) {
            return;
        }
        for (Document operation : operations) {
            for (Map.Entry<String, String> kind : Map.of("insert", Actions.INSERT, "update", Actions.UPDATE, "delete", Actions.REMOVE).entrySet()) {
                if (operation.get(kind.getKey()) instanceof Number index && index.intValue() < namespaces.size()) {
                    String namespace = namespaces.get(index.intValue()).getString("ns");
                    int dot = namespace == null ? -1 : namespace.indexOf('.');
                    if (dot > 0) {
                        add(found, kind.getValue(), namespace.substring(0, dot), namespace.substring(dot + 1));
                    }
                }
            }
        }
    }

    /** Follows a pipeline's stages to the collections they name. */
    private static void pipeline(@Nullable Object stages, String database, Set<Access> found, int depth)
    {
        if (!(stages instanceof List<?> list) || depth > MAX_DEPTH) {
            return;
        }
        for (Object stage : list) {
            if (stage instanceof Document document) {
                stage(document, database, found, depth);
            }
        }
    }

    private static void stage(Document stage, String database, Set<Access> found, int depth)
    {
        if (stage.get("$lookup") instanceof Document lookup) {
            named(lookup.get("from"), database, Actions.READ, found);
            pipeline(lookup.get("pipeline"), database, found, depth + 1);
        }
        if (stage.get("$graphLookup") instanceof Document graph) {
            named(graph.get("from"), database, Actions.READ, found);
        }
        Object union = stage.get("$unionWith");
        if (union instanceof Document with) {
            named(with.get("coll"), database, Actions.READ, found);
            pipeline(with.get("pipeline"), database, found, depth + 1);
        }
        else {
            named(union, database, Actions.READ, found);
        }
        if (stage.get("$facet") instanceof Document facet) {
            facet.values().forEach(value -> pipeline(value, database, found, depth + 1));
        }
        named(stage.get("$out"), database, Actions.INSERT, found);
        if (stage.get("$merge") instanceof Document merge) {
            named(merge.get("into"), database, Actions.INSERT, found);
        }
    }

    /** Adds a collection named by a string, or by a document with its database. */
    private static void named(@Nullable Object name, String database, String type, Set<Access> found)
    {
        if (name instanceof String collection) {
            add(found, type, database, collection);
        }
        else if (name instanceof Document document) {
            String other = document.getString("db");
            String collection = document.getString("coll");
            if (collection != null) {
                add(found, type, other == null ? database : other, collection);
            }
        }
    }

    private static void add(Set<Access> found, String type, String database, String collection)
    {
        if (!Inventory.SYSTEM_DATABASES.contains(database) && !collection.isEmpty() && !collection.startsWith(Inventory.SYSTEM_COLLECTIONS) && !collection.startsWith("$cmd")) {
            found.add(new Access(type, database, collection));
        }
    }

    private static void addDatabase(Set<Access> found, String type, String database)
    {
        if (!Inventory.SYSTEM_DATABASES.contains(database)) {
            found.add(new Access(type, database, null));
        }
    }
}
