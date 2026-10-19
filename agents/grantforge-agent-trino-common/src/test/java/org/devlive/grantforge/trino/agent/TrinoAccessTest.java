// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrinoAccessTest
{
    private static final Caller ALICE = new Caller("alice", List.of("analysts"), "20261019_1");
    private static final Caller BOB = new Caller("bob", List.of(), null);

    /** What Trino would throw: the tests read the message. */
    static final class Refused
            extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        Refused(String message)
        {
            super(message);
        }
    }

    private TestRuntime test = new TestRuntime(null);

    private Map<String, @Nullable Object> fields(int index)
    {
        return EventFields.of(test.events().get(index));
    }

    private TrinoAccess access(@Nullable Snapshot snapshot)
    {
        test = new TestRuntime(snapshot);
        return new TrinoAccess(test.runtime(), Refused::new);
    }

    private static TrinoSnapshots sales()
    {
        return new TrinoSnapshots().allow(List.of("@analysts"), List.of("select"), "catalog", "hive", "schema", "sales")
                .allow(List.of("alice"), List.of("insert", "alter"), "catalog", "hive", "schema", "sales", "table", "orders")
                .deny(List.of("alice"), List.of("select"), "catalog", "hive", "schema", "sales", "table", "orders", "column", "card")
                .allow(List.of("alice"), List.of("set"), "catalog", "hive", "session_property", "compression_*")
                .allow(List.of("alice"), List.of("execute"), "catalog", "hive", "schema", "udf", "function", "clean")
                .allow(List.of("alice"), List.of("view_query"), "user", "bob")
                .deny(List.of("@analysts"), List.of("select"), "catalog", "hive", "schema", "sales", "table", "returns", "column", "cost");
    }

    @Test
    void allowsAndReportsWhatAPolicyAllows()
    {
        TrinoAccess access = access(sales().snapshot());
        assertThat(access.runtime()).isSameAs(test.runtime());
        access.check(ALICE, "insert into table", TrinoResource.table("hive", "sales", "orders"), TrinoAccess.INSERT);
        assertThat(fields(0)).containsEntry("outcome", "ALLOWED").containsEntry("user", "alice").containsEntry("resource", "hive.sales.orders")
                .containsEntry("accessType", "insert").containsEntry("resourceType", "table").containsEntry("action", "insert into table")
                .containsEntry("request", "query 20261019_1").containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", "2");
        assertThat(access.allows(ALICE, "execute function", TrinoResource.function("hive", "udf", "clean"), TrinoAccess.EXECUTE)).isTrue();
        assertThat(access.allows(ALICE, "execute function", TrinoResource.function("hive", "udf", "other"), TrinoAccess.EXECUTE)).isFalse();
    }

    @Test
    void refusesAndNamesWhy()
    {
        TrinoAccess access = access(sales().snapshot());
        assertThatThrownBy(() -> access.check(ALICE, "drop table", TrinoResource.table("hive", "sales", "orders"), TrinoAccess.DROP))
                .isInstanceOf(Refused.class).hasMessage("alice may not drop hive.sales.orders: no GrantForge policy allows it");
        assertThatThrownBy(() -> access.check(BOB, "rename table", TrinoResource.table("hive", "sales", "orders"), TrinoAccess.ALTER,
                TrinoAccess.CREATE)).hasMessage("bob may not alter or create hive.sales.orders: no GrantForge policy allows it");
        assertThat(test.events()).extracting(AccessEvent::allowed).containsExactly(false, false);
        assertThat(fields(1)).as("no query").containsEntry("request", null);
    }

    @Test
    void checksEveryColumnAndReportsOnce()
    {
        TrinoAccess access = access(sales().snapshot());
        access.checkColumns(ALICE, "select", "hive", "sales", "orders", Set.of("region", "id"), TrinoAccess.SELECT);
        assertThat(test.events()).hasSize(1);
        assertThat(fields(0)).containsEntry("resource", "hive.sales.orders").containsEntry("request", "query 20261019_1; columns id, region");
        assertThatThrownBy(() -> access.checkColumns(ALICE, "select", "hive", "sales", "orders", Set.of("id", "card"), TrinoAccess.SELECT))
                .hasMessage("alice may not select hive.sales.orders.card: GrantForge policy 3 denies it");
        assertThat(fields(1)).containsEntry("resource", "hive.sales.orders.card").containsEntry("outcome", "DENIED");
        access.checkColumns(ALICE, "select", "hive", "sales", "orders", Set.of(), TrinoAccess.SELECT);
        assertThat(fields(2)).containsEntry("resource", "hive.sales.orders").containsEntry("request", "query 20261019_1");
        access.checkColumns(BOB, "select", "hive", "information_schema", "tables", Set.of("table_name"), TrinoAccess.SELECT);
        assertThat(test.events()).as("what is open is not reported").hasSize(3);
    }

    @Test
    void cutsALongRequestShort()
    {
        TrinoAccess access = access(new TrinoSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "hive").snapshot());
        Set<String> columns = new LinkedHashSet<>();
        for (int index = 0; index < 300; index++) {
            columns.add("column_" + index);
        }
        access.checkColumns(ALICE, "select", "hive", "sales", "wide", columns, TrinoAccess.SELECT);
        assertThat(String.valueOf(fields(0).get("request"))).hasSize(AccessEvent.MAX_REQUEST).endsWith("...");
    }

    @Test
    void saysWhenNoPoliciesCameYet()
    {
        TrinoAccess access = access(null);
        assertThatThrownBy(() -> access.check(ALICE, "select", TrinoResource.table("hive", "sales", "orders"), TrinoAccess.SELECT))
                .hasMessage("alice may not select hive.sales.orders: GrantForge has sent this coordinator no policies yet");
        assertThat(access.canAccessCatalog(ALICE, "hive")).isFalse();
        assertThat(access.canAccessCatalog(ALICE, "system")).isTrue();
    }

    @Test
    void leavesTrinoUncheckedWhileTheServiceIsNotInUse()
    {
        TrinoAccess access = access(new TrinoSnapshots().disabled().snapshot());
        access.check(BOB, "drop table", TrinoResource.table("hive", "sales", "orders"), TrinoAccess.DROP);
        access.checkColumns(BOB, "select", "hive", "sales", "orders", Set.of("card"), TrinoAccess.SELECT);
        assertThat(access.managed(BOB, "grant", "hive.sales.orders")).as("refused all the same").hasMessageEndingWith(TrinoAccess.MANAGED);
        assertThat(access.filterCatalogs(BOB, new LinkedHashSet<>(List.of("hive", "iceberg")))).containsExactly("hive", "iceberg");
        assertThat(access.filterQueryOwners(BOB, List.of("alice"), owner -> owner)).containsExactly("alice");
        assertThat(access.rowFilter(BOB, "hive", "sales", "orders")).isNull();
        assertThat(access.mask(BOB, "hive", "sales", "orders", "card", "varchar")).isNull();
        assertThat(test.events()).isEmpty();
    }

    @Test
    void refusesTrinosOwnPrivilegesAndRoles()
    {
        TrinoAccess access = access(sales().snapshot());
        assertThat(access.managed(ALICE, "grant table privilege", "hive.sales.orders")).isInstanceOf(Refused.class)
                .hasMessage("alice may not grant table privilege on hive.sales.orders: " + TrinoAccess.MANAGED);
    }

    @Test
    void listsWhatACallerMayUse()
    {
        TrinoAccess access = access(sales().snapshot());
        assertThat(access.filterCatalogs(ALICE, new LinkedHashSet<>(List.of("hive", "iceberg", "system")))).containsExactly("hive", "system");
        assertThat(access.canAccessCatalog(BOB, "hive")).isFalse();
        assertThat(access.filterSchemas(ALICE, "hive", new LinkedHashSet<>(List.of("sales", "hr", "udf", "information_schema"))))
                .containsExactly("sales", "udf", "information_schema");
        assertThat(access.filterSchemas(BOB, "system", Set.of("jdbc", "runtime"))).containsExactly("jdbc");
        assertThat(access.filterTables(ALICE, "hive", new LinkedHashSet<>(List.of(List.of("sales", "orders"), List.of("sales", "returns"),
                List.of("hr", "people"), List.of("information_schema", "columns"))), table -> table.get(0), table -> table.get(1)))
                .containsExactly(List.of("sales", "orders"), List.of("sales", "returns"), List.of("information_schema", "columns"));
        assertThat(access.filterColumns(ALICE, "hive", "sales", "returns", new LinkedHashSet<>(List.of("id", "cost")))).containsExactly("id");
        assertThat(access.filterColumns(ALICE, "hive", "sales", "orders", new LinkedHashSet<>(List.of("id", "card"))))
                .as("what alice may write she sees").containsExactly("id", "card");
        assertThat(access.filterFunctions(ALICE, "hive", new LinkedHashSet<>(List.of(List.of("udf", "clean"), List.of("udf", "other"))),
                function -> function.get(0), function -> function.get(1))).containsExactly(List.of("udf", "clean"));
        assertThat(access.filterFunctions(BOB, "system", Set.of(List.of("builtin", "abs")), function -> function.get(0), function -> function.get(1)))
                .hasSize(1);
        assertThat(test.events()).as("listings are not reported").isEmpty();
    }

    @Test
    void checksWhatACallerMaySee()
    {
        TrinoAccess access = access(sales().snapshot());
        access.checkVisible(ALICE, "show tables", TrinoResource.schema("hive", "sales"));
        access.checkVisible(ALICE, "show functions", TrinoResource.schema("hive", "udf"));
        access.checkVisible(BOB, "show tables", TrinoResource.schema("hive", "information_schema"));
        assertThatThrownBy(() -> access.checkVisible(BOB, "show tables", TrinoResource.schema("hive", "sales")))
                .hasMessage("bob may not show tables in hive.sales: no GrantForge policy allows anything there");
    }

    @Test
    void keepsQueriesOfOthersUnderPolicy()
    {
        TrinoAccess access = access(sales().snapshot());
        access.checkQueryOwner(ALICE, "alice", TrinoAccess.KILL_QUERY);
        access.checkQueryOwner(ALICE, "bob", TrinoAccess.VIEW_QUERY);
        assertThatThrownBy(() -> access.checkQueryOwner(ALICE, "bob", TrinoAccess.KILL_QUERY)).hasMessage(
                "alice may not kill_query user bob: no GrantForge policy allows it");
        assertThat(test.events()).hasSize(2);
        assertThat(fields(0)).containsEntry("action", "view query").containsEntry("resource", "user bob").containsEntry("outcome", "ALLOWED");
        assertThat(fields(1)).containsEntry("action", "kill query").containsEntry("outcome", "DENIED");
        assertThat(access.filterQueryOwners(ALICE, List.of("alice", "bob", "carol"), owner -> owner)).containsExactly("alice", "bob");
    }

    @Test
    void opensWhatDescribesData()
    {
        assertThat(TrinoAccess.open(TrinoResource.table("hive", "information_schema", "tables"))).isTrue();
        assertThat(TrinoAccess.open(TrinoResource.column("hive", "information_schema", "tables", "table_name"))).isTrue();
        assertThat(TrinoAccess.open(TrinoResource.schema("hive", "information_schema"))).isTrue();
        assertThat(TrinoAccess.open(TrinoResource.function("hive", "information_schema", "f"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.table("system", "jdbc", "tables"))).isTrue();
        assertThat(TrinoAccess.open(TrinoResource.table("system", "metadata", "catalogs"))).isTrue();
        assertThat(TrinoAccess.open(TrinoResource.table("system", "runtime", "queries"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.procedure("system", "metadata", "p"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.function("system", "builtin", "abs"))).isTrue();
        assertThat(TrinoAccess.open(TrinoResource.function("system", "jdbc", "abs"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.table("hive", "jdbc", "tables"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.catalog("system"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.sessionProperty("system", "x"))).isFalse();
        assertThat(TrinoAccess.open(TrinoResource.user("bob"))).isFalse();
    }

    @Test
    void filtersRowsAndMasksColumns()
    {
        TrinoAccess access = access(new TrinoSnapshots().filter(List.of("alice"), "region = 'EU'", "catalog", "hive", "schema", "sales", "table",
                "orders").mask(List.of("alice"), "mask_hash", null, "catalog", "hive", "schema", "sales", "table", "orders", "column", "customer")
                .snapshot());
        assertThat(access.rowFilter(ALICE, "hive", "sales", "orders")).isEqualTo("region = 'EU'");
        assertThat(access.rowFilter(BOB, "hive", "sales", "orders")).isNull();
        assertThat(access.rowFilter(ALICE, "hive", "information_schema", "tables")).isNull();
        assertThat(access.mask(ALICE, "hive", "sales", "orders", "customer", "varchar")).isEqualTo("CAST((to_hex(sha256(to_utf8(\"customer\")))) AS varchar)");
        assertThat(access.mask(ALICE, "hive", "sales", "orders", "amount", "bigint")).isNull();
        assertThat(access.mask(ALICE, "system", "jdbc", "tables", "table_name", "varchar")).isNull();
    }

    @Test
    void failsClosedWhenTheSnapshotCannotTell()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceEnabled()).thenReturn(true);
        when(broken.serviceType()).thenReturn("trino");
        when(broken.rowFilter(any())).thenThrow(new IllegalStateException("corrupt"));
        when(broken.mask(any())).thenThrow(new IllegalStateException("corrupt"));
        TrinoAccess access = access(broken);
        assertThat(access.rowFilter(ALICE, "hive", "sales", "orders")).isEqualTo("false");
        assertThat(access.mask(ALICE, "hive", "sales", "orders", "customer", "varchar(5)")).isEqualTo("CAST(NULL AS varchar(5))");
    }
}
