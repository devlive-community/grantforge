// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject.HivePrivObjectActionType;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject.HivePrivilegeObjectType;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HiveAccessTest
{
    static HivePrivilegeObject object(@Nullable HivePrivilegeObjectType type, @Nullable String database, @Nullable String name,
            @Nullable List<String> columns, @Nullable HivePrivObjectActionType action)
    {
        return new HivePrivilegeObject(type, database, name, null, columns, action, null, null);
    }

    static HivePrivilegeObject table(String database, String name, String... columns)
    {
        return object(HivePrivilegeObjectType.TABLE_OR_VIEW, database, name, columns.length == 0 ? null : List.of(columns),
                HivePrivObjectActionType.OTHER);
    }

    static HivePrivilegeObject database(String database)
    {
        return object(HivePrivilegeObjectType.DATABASE, database, null, null, HivePrivObjectActionType.OTHER);
    }

    private static List<String> described(String operation, HivePrivilegeObject object, boolean input)
    {
        return HiveAccess.checks(operation, object, input).stream().map(HiveCheck::toString).toList();
    }

    @Test
    void queriesReadEachColumnAndWritesNeedUpdate()
    {
        assertThat(described("QUERY", table("sales", "orders", "id", "card"), true)).containsExactly("select on sales.orders.id",
                "select on sales.orders.card");
        assertThat(described("QUERY", table("sales", "orders"), true)).containsExactly("select on sales.orders");
        for (HivePrivObjectActionType write : List.of(HivePrivObjectActionType.INSERT, HivePrivObjectActionType.INSERT_OVERWRITE,
                HivePrivObjectActionType.UPDATE, HivePrivObjectActionType.DELETE)) {
            assertThat(described("QUERY", object(HivePrivilegeObjectType.TABLE_OR_VIEW, "sales", "orders", null, write), false))
                    .containsExactly("update on sales.orders");
        }
        assertThat(described("QUERY", object(HivePrivilegeObjectType.PARTITION, "sales", "orders", List.of("id"),
                HivePrivObjectActionType.OTHER), true)).as("a partition is its table's").containsExactly("select on sales.orders");
        assertThat(described("QUERY", object(HivePrivilegeObjectType.TABLE_OR_VIEW, "sales", "orders", List.of(), null), true))
                .containsExactly("select on sales.orders");
    }

    @Test
    void createsReadTheirSourcesAndCreateTheirTargets()
    {
        assertThat(described("CREATETABLE_AS_SELECT", table("sales", "orders"), true)).containsExactly("select on sales.orders");
        assertThat(described("CREATETABLE_AS_SELECT", table("sales", "copy"), false)).containsExactly("create on sales.copy");
        assertThat(described("CREATETABLE", database("sales"), false)).as("the database of a new table").isEmpty();
        assertThat(described("CREATEVIEW", object(HivePrivilegeObjectType.FUNCTION, "sales", "upper2", null,
                HivePrivObjectActionType.OTHER), true)).containsExactly("select on sales.upper2");
        assertThat(described("CREATEVIEW", object(HivePrivilegeObjectType.DFS_URI, null, "hdfs://nn/x", null,
                HivePrivObjectActionType.OTHER), true)).containsExactly("read on hdfs://nn/x");
        assertThat(HiveAccess.accessType("CREATE_MATERIALIZED_VIEW", "DATABASE", "OTHER", false)).isNull();
        assertThat(described("CREATEDATABASE", database("sales"), false)).containsExactly("create on sales");
        assertThat(HiveAccess.accessType("CREATEDATABASE", "DFS_URI", "OTHER", false)).isNull();
        assertThat(described("CREATEFUNCTION", object(HivePrivilegeObjectType.FUNCTION, "sales", "f", null, HivePrivObjectActionType.OTHER),
                false)).containsExactly("create on sales.f");
        assertThat(described("CREATEFUNCTION", database("sales"), false)).isEmpty();
        assertThat(described("IMPORT", table("sales", "orders"), false)).containsExactly("create on sales.orders");
        assertThat(described("IMPORT", table("sales", "orders"), true)).containsExactly("select on sales.orders");
    }

    @Test
    void altersDropsLocksAndAdministration()
    {
        assertThat(described("ALTERTABLE_ADDCOLS", table("sales", "orders"), false)).containsExactly("alter on sales.orders");
        assertThat(described("ALTERTABLE_EXCHANGEPARTITION", table("sales", "orders"), false)).containsExactly("alter on sales.orders");
        assertThat(described("ALTERVIEW_AS", table("sales", "view"), false)).containsExactly("alter on sales.view");
        assertThat(described("ALTERVIEW_AS", table("sales", "orders"), true)).containsExactly("select on sales.orders");
        assertThat(described("ALTERVIEW_AS", database("sales"), true)).containsExactly("select on sales");
        assertThat(HiveAccess.accessType("ALTERVIEW_AS", "FUNCTION", "OTHER", true)).isNull();
        assertThat(described("DROPTABLE", table("sales", "orders"), false)).containsExactly("drop on sales.orders");
        assertThat(described("LOCKTABLE", table("sales", "orders"), true)).containsExactly("lock on sales.orders");
        assertThat(described("TRUNCATETABLE", table("sales", "orders"), false)).containsExactly("update on sales.orders");
        assertThat(described("EXPORT", table("sales", "orders"), true)).containsExactly("select on sales.orders");
        assertThat(described("LOAD", table("sales", "orders"), false)).containsExactly("update on sales.orders");
        assertThat(described("REPLDUMP", database("sales"), true)).containsExactly("repladmin on sales");
        assertThat(described("KILL_QUERY", object(HivePrivilegeObjectType.SERVICE_NAME, null, "hiveserver2", null,
                HivePrivObjectActionType.OTHER), true)).containsExactly("serviceadmin on hiveserver2");
        assertThat(described("KILL_QUERY", object(HivePrivilegeObjectType.SERVICE_NAME, null, null, null,
                HivePrivObjectActionType.OTHER), true)).containsExactly("serviceadmin on hiveservice");
        assertThat(described("SHOWCONF", object(HivePrivilegeObjectType.SERVICE_NAME, null, "hs2", null, HivePrivObjectActionType.OTHER),
                true)).isEmpty();
    }

    @Test
    void listingAndSwitchingNeedAnyAccess()
    {
        for (String operation : List.of("SHOWDATABASES", "SWITCHDATABASE", "DESCDATABASE", "SHOWTABLES", "SHOWVIEWS")) {
            assertThat(HiveAccess.checks(operation, database("sales"), true)).extracting(HiveCheck::accessType)
                    .containsExactly(AccessRequest.ANY_ACCESS);
        }
        assertThat(described("SHOWCOLUMNS", table("sales", "orders"), true)).containsExactly("select on sales.orders");
        assertThat(HiveAccess.checks("SWITCHDATABASE", object(null, "sales", null, null, null), true)).extracting(HiveCheck::resource)
                .containsExactly(Map.of("database", "sales"));
        assertThat(HiveAccess.checks("DESCDATABASE", object(HivePrivilegeObjectType.DATABASE, null, "sales", null, null), true))
                .extracting(HiveCheck::display).containsExactly("sales");
    }

    @Test
    void temporaryFunctionsAndAddedFilesAreTheGlobalScopes()
    {
        assertThat(described("CREATEFUNCTION", object(HivePrivilegeObjectType.FUNCTION, null, "tmp_upper", null,
                HivePrivObjectActionType.OTHER), false)).containsExactly("tempudfadmin on tmp_upper");
        assertThat(described("DROPFUNCTION", object(HivePrivilegeObjectType.FUNCTION, "", null, null, HivePrivObjectActionType.OTHER),
                false)).containsExactly("tempudfadmin on global");
        assertThat(described("QUERY", object(HivePrivilegeObjectType.FUNCTION, null, "tmp_upper", null, HivePrivObjectActionType.OTHER),
                true)).as("using a temporary function").isEmpty();
        assertThat(described("ADD", object(HivePrivilegeObjectType.COMMAND_PARAMS, null, null, null, HivePrivObjectActionType.OTHER),
                true)).containsExactly("tempudfadmin on global");
        assertThat(described("COMPILE", object(HivePrivilegeObjectType.GLOBAL, null, "x", null, HivePrivObjectActionType.OTHER), true))
                .containsExactly("tempudfadmin on x");
        assertThat(described("DFS", object(HivePrivilegeObjectType.COMMAND_PARAMS, null, null, null, HivePrivObjectActionType.OTHER),
                true)).isEmpty();
        assertThat(described("QUERY", object(HivePrivilegeObjectType.FUNCTION, "sales", "my_upper", null, HivePrivObjectActionType.OTHER),
                true)).containsExactly("select on sales.my_upper");
    }

    @Test
    void urlsAreReadAndWritten()
    {
        HivePrivilegeObject local = object(HivePrivilegeObjectType.LOCAL_URI, null, "file:/tmp/in", null, HivePrivObjectActionType.OTHER);
        assertThat(described("LOAD", local, true)).containsExactly("read on file:/tmp/in");
        assertThat(described("EXPORT", object(HivePrivilegeObjectType.DFS_URI, null, "hdfs://nn/out", null,
                HivePrivObjectActionType.INSERT), false)).containsExactly("write on hdfs://nn/out");
        assertThat(HiveAccess.checks("LOAD", local, true).get(0).level()).isEqualTo("url");
    }

    @Test
    void leavesOtherObjectsAndOperationsToHive()
    {
        assertThat(described("EXPLAIN", table("sales", "orders"), true)).isEmpty();
        assertThat(described("GRANT_PRIVILEGE", table("sales", "orders"), true)).isEmpty();
        assertThat(described("QUERY", object(HivePrivilegeObjectType.COLUMN, "sales", "orders", null, HivePrivObjectActionType.OTHER),
                true)).isEmpty();
        assertThat(HiveAccess.accessType("QUERY", "TABLE_OR_VIEW", "MERGE", true)).as("an action this agent does not know").isNull();
        assertThatThrownBy(() -> HiveAccess.checks("QUERY", table("sales", ""), true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no table");
    }

    @Test
    void operationsWithoutObjectsNeedTheirAccessOnEveryDatabase()
    {
        assertThat(HiveAccess.withoutObjects("REPLLOAD")).extracting(HiveCheck::toString).containsExactly("repladmin on *");
        assertThat(HiveAccess.withoutObjects("ALTERTABLE_OWNER")).extracting(HiveCheck::toString).containsExactly("alter on *");
        assertThat(HiveAccess.withoutObjects("ALTERDATABASE_OWNER")).extracting(HiveCheck::toString).containsExactly("alter on *");
        assertThat(HiveAccess.withoutObjects("SHOWDATABASES")).isEmpty();
    }

    @Test
    void writesAltersAndExportsBypassMasks()
    {
        HiveCheck update = HiveAccess.checks("QUERY", object(HivePrivilegeObjectType.TABLE_OR_VIEW, "sales", "orders", null,
                HivePrivObjectActionType.INSERT), false).get(0);
        HiveCheck read = HiveAccess.checks("QUERY", table("sales", "orders", "card"), true).get(0);
        assertThat(HiveAccess.bypassesMasks("QUERY", update)).isTrue();
        assertThat(HiveAccess.bypassesMasks("ALTERTABLE_LOCATION", HiveAccess.checks("ALTERTABLE_LOCATION", table("sales", "orders"), false)
                .get(0))).isTrue();
        assertThat(HiveAccess.bypassesMasks("EXPORT", read)).isTrue();
        assertThat(HiveAccess.bypassesMasks("QUERY", read)).isFalse();
        assertThat(HiveAccess.bypassesMasks("DROPDATABASE", HiveAccess.checks("DROPDATABASE", database("sales"), false).get(0))).isFalse();
    }
}
