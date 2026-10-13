// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a Hive operation needs of each object it reads or writes, as Apache Ranger's Hive authorizer decides it: writing
 * rows needs {@code update}; creating, altering, dropping and locking need their own access types; queries and most
 * SHOW and DESCRIBE statements need {@code select}; listing and switching databases need any access to the database or
 * something in it; replication, HiveServer2's administration and temporary UDFs need their admin access types; URLs
 * are read and written. Operations and object types are compared by name, so the releases that add some keep working.
 */
final class HiveAccess
{
    /** Database, table, column and permanent UDF levels, then URLs, HiveServer2 and the global scope. */
    static final String DATABASE = "database";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String UDF = "udf";
    static final String URL = "url";
    static final String SERVICE = "hiveservice";
    static final String GLOBAL = "global";

    static final String SELECT = "select";
    static final String UPDATE = "update";
    static final String ALTER = "alter";

    private static final Map<String, String> BY_OPERATION = new HashMap<>();
    private static final Set<String> WRITES = new HashSet<>(Arrays.asList("INSERT", "INSERT_OVERWRITE", "UPDATE", "DELETE"));
    private static final Set<String> CREATES = new HashSet<>(Arrays.asList("CREATETABLE", "CREATEVIEW", "CREATETABLE_AS_SELECT",
            "CREATE_MATERIALIZED_VIEW"));
    private static final Set<String> TEMPORARY_FUNCTIONS = new HashSet<>(Arrays.asList("CREATEFUNCTION", "DROPFUNCTION"));
    private static final Set<String> GLOBAL_COMMANDS = new HashSet<>(Arrays.asList("ADD", "COMPILE"));

    static {
        operations(ALTER, "ALTERDATABASE", "ALTERDATABASE_LOCATION", "ALTERDATABASE_OWNER", "ALTERPARTITION_BUCKETNUM",
                "ALTERPARTITION_FILEFORMAT", "ALTERPARTITION_LOCATION", "ALTERPARTITION_MERGEFILES", "ALTERPARTITION_PROTECTMODE",
                "ALTERPARTITION_SERDEPROPERTIES", "ALTERPARTITION_SERIALIZER", "ALTERTABLE_ADDCOLS", "ALTERTABLE_ADDPARTS",
                "ALTERTABLE_ARCHIVE", "ALTERTABLE_BUCKETNUM", "ALTERTABLE_CLUSTER_SORT", "ALTERTABLE_COMPACT", "ALTERTABLE_DROPPARTS",
                "ALTERTABLE_DROPCONSTRAINT", "ALTERTABLE_ADDCONSTRAINT", "ALTERTABLE_FILEFORMAT", "ALTERTABLE_LOCATION",
                "ALTERTABLE_MERGEFILES", "ALTERTABLE_PARTCOLTYPE", "ALTERTABLE_PROPERTIES", "ALTERTABLE_SETPARTSPEC", "ALTERTABLE_EXECUTE",
                "ALTERTABLE_CONVERT", "ALTERDATACONNECTOR", "ALTERDATACONNECTOR_OWNER", "ALTERDATACONNECTOR_URL", "ALTERTABLE_PROTECTMODE",
                "ALTERTABLE_RENAME", "ALTERTABLE_RENAMECOL", "ALTERTABLE_RENAMEPART", "ALTERTABLE_REPLACECOLS", "ALTERTABLE_SERDEPROPERTIES",
                "ALTERTABLE_SERIALIZER", "ALTERTABLE_SKEWED", "ALTERTABLE_TOUCH", "ALTERTABLE_UNARCHIVE", "ALTERTABLE_UPDATEPARTSTATS",
                "ALTERTABLE_UPDATETABLESTATS", "ALTERTABLE_UPDATECOLUMNS", "ALTERTABLE_CREATEBRANCH", "ALTERTABLE_DROPBRANCH",
                "ALTERTABLE_CREATETAG", "ALTERTABLE_DROPTAG", "ALTERTBLPART_SKEWED_LOCATION", "ALTERVIEW_PROPERTIES", "ALTERVIEW_RENAME",
                "ALTER_MATERIALIZED_VIEW_REWRITE", "ALTER_MATERIALIZED_VIEW_REBUILD", "MSCK", "ALTERTABLE_OWNER",
                "ALTERTABLE_EXCHANGEPARTITION");
        operations("drop", "DROPFUNCTION", "DROPTABLE", "DROPVIEW", "DROP_MATERIALIZED_VIEW", "DROPDATABASE", "DROPDATACONNECTOR");
        operations("lock", "LOCKDB", "LOCKTABLE", "UNLOCKDB", "UNLOCKTABLE");
        // Ranger's choice: these show what a table holds or how it is built, so they need what a query needs.
        operations(SELECT, "QUERY", "SHOW_TABLESTATUS", "SHOW_CREATETABLE", "SHOWPARTITIONS", "SHOW_TBLPROPERTIES", "ANALYZE_TABLE",
                "SHOWCOLUMNS", "DESCTABLE");
        operations(AccessRequest.ANY_ACCESS, "SHOWDATABASES", "SHOWDATACONNECTORS", "SHOW_GRANT", "SWITCHDATABASE", "DESCDATABASE",
                "DESCDATACONNECTOR", "SHOWTABLES", "SHOWVIEWS");
        operations(UPDATE, "TRUNCATETABLE");
        operations("repladmin", "REPLDUMP", "REPLLOAD", "REPLSTATUS");
        operations("serviceadmin", "KILL_QUERY", "CREATE_RESOURCEPLAN", "SHOW_RESOURCEPLAN", "ALTER_RESOURCEPLAN", "DROP_RESOURCEPLAN",
                "CREATE_TRIGGER", "ALTER_TRIGGER", "DROP_TRIGGER", "CREATE_POOL", "ALTER_POOL", "DROP_POOL", "CREATE_MAPPING", "ALTER_MAPPING",
                "DROP_MAPPING", "LLAP_CACHE_PURGE", "LLAP_CLUSTER_INFO");
        operations("tempudfadmin", "ADD", "COMPILE");
    }

    private HiveAccess()
    {
    }

    private static void operations(String accessType, String... names)
    {
        for (String name : names) {
            BY_OPERATION.put(name, accessType);
        }
    }

    /**
     * Returns the checks one object of an operation needs.
     *
     * @param operation the operation, by its {@code HiveOperationType} name
     * @param object the object
     * @param input whether the operation reads the object, rather than writes it
     * @return the checks, one per column of a column list; none for objects the agent leaves to Hive
     * @throws IllegalArgumentException when Hive does not name an object the agent checks
     */
    static List<HiveCheck> checks(String operation, HivePrivilegeObject object, boolean input)
    {
        String type = object.getType() == null ? "DATABASE" : object.getType().name();
        String database = object.getDbname();
        String name = object.getObjectName();
        String action = object.getActionType() == null ? "OTHER" : object.getActionType().name();
        switch (type) {
            case "DATABASE": {
                String access = accessType(operation, type, action, input);
                return access == null ? Collections.<HiveCheck>emptyList()
                        : Collections.singletonList(new HiveCheck(access, DATABASE, database == null ? name : database));
            }
            case "TABLE_OR_VIEW":
            case "PARTITION": {
                String access = accessType(operation, "TABLE_OR_VIEW", action, input);
                if (access == null) {
                    return Collections.emptyList();
                }
                List<String> columns = object.getColumns();
                if (columns == null || columns.isEmpty() || "PARTITION".equals(type)) {
                    return Collections.singletonList(new HiveCheck(access, DATABASE, database, TABLE, name));
                }
                List<HiveCheck> checks = new ArrayList<>(columns.size());
                for (String column : columns) {
                    checks.add(new HiveCheck(access, DATABASE, database, TABLE, name, COLUMN, column));
                }
                return checks;
            }
            case "FUNCTION":
                if (database == null || database.isEmpty()) {
                    // A temporary function belongs to no database: creating or dropping one is the global scope's.
                    return TEMPORARY_FUNCTIONS.contains(operation) ? Collections.singletonList(global(name)) : Collections.<HiveCheck>emptyList();
                }
                return single(accessType(operation, type, action, input), DATABASE, database, UDF, name);
            case "DFS_URI":
            case "LOCAL_URI":
                return Collections.singletonList(new HiveCheck(input ? "read" : "write", URL, name));
            case "COMMAND_PARAMS":
            case "GLOBAL":
                return GLOBAL_COMMANDS.contains(operation) ? Collections.singletonList(global(name)) : Collections.<HiveCheck>emptyList();
            case "SERVICE_NAME":
                return single(accessType(operation, type, action, input), SERVICE, name == null || name.isEmpty() ? SERVICE : name);
            default:
                return Collections.emptyList();
        }
    }

    private static List<HiveCheck> single(@Nullable String access, String... levels)
    {
        return access == null ? Collections.<HiveCheck>emptyList() : Collections.singletonList(new HiveCheck(access, levels));
    }

    private static HiveCheck global(@Nullable String name)
    {
        return new HiveCheck("tempudfadmin", GLOBAL, name == null || name.isEmpty() ? GLOBAL : name);
    }

    /**
     * Returns the checks of an operation Hive names no object for, which Ranger reads from the statement: replication
     * and handing over a table or database then need the access on every database.
     *
     * @param operation the operation
     * @return the checks; none for the other operations
     */
    static List<HiveCheck> withoutObjects(String operation)
    {
        String replication = "repladmin";
        if (replication.equals(BY_OPERATION.get(operation))) {
            return Collections.singletonList(new HiveCheck(replication, DATABASE, "*"));
        }
        if ("ALTERTABLE_OWNER".equals(operation) || "ALTERDATABASE_OWNER".equals(operation)) {
            return Collections.singletonList(new HiveCheck(ALTER, DATABASE, "*"));
        }
        return Collections.emptyList();
    }

    /**
     * Returns the access type an operation needs of an object.
     *
     * @param operation the operation
     * @param type the object's type, by name
     * @param action what the operation does to it: {@code OTHER} or a kind of write
     * @param input whether the operation reads it
     * @return the access type, or {@code null} when the object is not checked
     */
    static @Nullable String accessType(String operation, String type, String action, boolean input)
    {
        if (WRITES.contains(action)) {
            return UPDATE;
        }
        if (!"OTHER".equals(action)) {
            return null;
        }
        if (CREATES.contains(operation)) {
            if ("TABLE_OR_VIEW".equals(type)) {
                return input ? SELECT : "create";
            }
            return "FUNCTION".equals(type) ? SELECT : null;
        }
        switch (operation) {
            case "CREATEDATABASE":
                return "DATABASE".equals(type) ? "create" : null;
            case "CREATEFUNCTION":
                return "FUNCTION".equals(type) ? "create" : null;
            case "ALTERVIEW_AS":
                if ("TABLE_OR_VIEW".equals(type)) {
                    return input ? SELECT : ALTER;
                }
                return "DATABASE".equals(type) ? SELECT : null;
            case "IMPORT":
                return input ? SELECT : "create";
            case "EXPORT":
            case "LOAD":
                return input ? SELECT : UPDATE;
            default:
                return BY_OPERATION.get(operation);
        }
    }

    /**
     * Returns whether a check gives a way around the masks and row filters of its table, as Ranger blocks it: exporting
     * the table's files, and writing or altering the table.
     *
     * @param operation the operation
     * @param check the check
     * @return whether the masks and row filters of its table refuse it
     */
    static boolean bypassesMasks(String operation, HiveCheck check)
    {
        return !check.table().isEmpty() && ("EXPORT".equals(operation) || UPDATE.equals(check.accessType()) || ALTER.equals(check.accessType()));
    }
}
