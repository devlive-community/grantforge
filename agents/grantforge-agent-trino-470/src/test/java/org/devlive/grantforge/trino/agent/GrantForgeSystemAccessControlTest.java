// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.QueryId;
import io.trino.spi.connector.CatalogSchemaName;
import io.trino.spi.connector.CatalogSchemaRoutineName;
import io.trino.spi.connector.CatalogSchemaTableName;
import io.trino.spi.connector.ColumnSchema;
import io.trino.spi.connector.EntityKindAndName;
import io.trino.spi.connector.EntityPrivilege;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.function.SchemaFunctionName;
import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.Identity;
import io.trino.spi.security.PrincipalType;
import io.trino.spi.security.Privilege;
import io.trino.spi.security.SystemAccessControl;
import io.trino.spi.security.SystemSecurityContext;
import io.trino.spi.security.TrinoPrincipal;
import io.trino.spi.security.ViewExpression;
import io.trino.spi.type.BigintType;
import io.trino.spi.type.VarcharType;
import org.devlive.grantforge.agent.EventFields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantForgeSystemAccessControlTest
{
    private static final Identity ALICE = Identity.forUser("alice").withGroups(Set.of("analysts")).build();
    private static final Identity BOB = Identity.ofUser("bob");
    private static final CatalogSchemaTableName ORDERS = new CatalogSchemaTableName("hive", "sales", "orders");
    private static final CatalogSchemaTableName RENAMED = new CatalogSchemaTableName("hive", "sales", "orders_2026");
    private static final CatalogSchemaName SALES = new CatalogSchemaName("hive", "sales");
    private static final CatalogSchemaRoutineName CLEAN = new CatalogSchemaRoutineName("hive", "udf", "clean");
    private static final TrinoPrincipal CAROL = new TrinoPrincipal(PrincipalType.USER, "carol");

    private final TestRuntime test = new TestRuntime(new TrinoSnapshots()
            .allow(List.of("alice"), List.of("all"), "catalog", "hive")
            .deny(List.of("alice"), List.of("select"), "catalog", "hive", "schema", "sales", "table", "orders", "column", "card")
            .allow(List.of("alice"), List.of("execute", "create_function", "drop_function"), "catalog", "hive", "schema", "udf", "function", "*")
            .allow(List.of("alice"), List.of("execute"), "catalog", "hive", "schema", "udf", "procedure", "*")
            .allow(List.of("alice"), List.of("set"), "catalog", "hive", "session_property", "*")
            .allow(List.of("alice"), List.of("set"), "system_session_property", "*")
            .allow(List.of("alice"), List.of("impersonate", "view_query", "kill_query"), "user", "carol")
            .allow(List.of("alice"), List.of("read", "write"), "system_information", "*")
            .filter(List.of("alice"), "region = 'EU'", "catalog", "hive", "schema", "sales", "table", "orders")
            .mask(List.of("alice"), "mask_hash", null, "catalog", "hive", "schema", "sales", "table", "orders", "column", "customer")
            .snapshot());
    private final GrantForgeSystemAccessControl control = new GrantForgeSystemAccessControl(new TrinoAccess(test.runtime(), AccessDeniedException::new));

    private static SystemSecurityContext context(Identity identity)
    {
        return new SystemSecurityContext(identity, new QueryId("20261019_000001_00000_abcde"), Instant.now());
    }

    /** Every check of the data a user is given, as alice may and bob may not. */
    private static Map<String, ThrowingConsumer<SystemSecurityContext>> checks(SystemAccessControl control)
    {
        Map<String, ThrowingConsumer<SystemSecurityContext>> checks = new LinkedHashMap<>();
        checks.put("create catalog", context -> control.checkCanCreateCatalog(context, "hive"));
        checks.put("drop catalog", context -> control.checkCanDropCatalog(context, "hive"));
        checks.put("show schemas", context -> control.checkCanShowSchemas(context, "hive"));
        checks.put("create schema", context -> control.checkCanCreateSchema(context, SALES, Map.of()));
        checks.put("drop schema", context -> control.checkCanDropSchema(context, SALES));
        checks.put("rename schema", context -> control.checkCanRenameSchema(context, SALES, "sales_2026"));
        checks.put("set schema authorization", context -> control.checkCanSetSchemaAuthorization(context, SALES, CAROL));
        checks.put("show create schema", context -> control.checkCanShowCreateSchema(context, SALES));
        checks.put("show tables", context -> control.checkCanShowTables(context, SALES));
        checks.put("show functions", context -> control.checkCanShowFunctions(context, SALES));
        checks.put("show create table", context -> control.checkCanShowCreateTable(context, ORDERS));
        checks.put("show columns", context -> control.checkCanShowColumns(context, ORDERS));
        checks.put("create table", context -> control.checkCanCreateTable(context, ORDERS, Map.of()));
        checks.put("drop table", context -> control.checkCanDropTable(context, ORDERS));
        checks.put("rename table", context -> control.checkCanRenameTable(context, ORDERS, RENAMED));
        checks.put("set table properties", context -> control.checkCanSetTableProperties(context, ORDERS, Map.of()));
        checks.put("comment on table", context -> control.checkCanSetTableComment(context, ORDERS));
        checks.put("comment on view", context -> control.checkCanSetViewComment(context, ORDERS));
        checks.put("comment on column", context -> control.checkCanSetColumnComment(context, ORDERS));
        checks.put("add column", context -> control.checkCanAddColumn(context, ORDERS));
        checks.put("alter column", context -> control.checkCanAlterColumn(context, ORDERS));
        checks.put("drop column", context -> control.checkCanDropColumn(context, ORDERS));
        checks.put("rename column", context -> control.checkCanRenameColumn(context, ORDERS));
        checks.put("set table authorization", context -> control.checkCanSetTableAuthorization(context, ORDERS, CAROL));
        checks.put("select", context -> control.checkCanSelectFromColumns(context, ORDERS, Set.of("id", "region")));
        checks.put("insert", context -> control.checkCanInsertIntoTable(context, ORDERS));
        checks.put("delete", context -> control.checkCanDeleteFromTable(context, ORDERS));
        checks.put("truncate table", context -> control.checkCanTruncateTable(context, ORDERS));
        checks.put("update", context -> control.checkCanUpdateTableColumns(context, ORDERS, Set.of("region")));
        checks.put("create view", context -> control.checkCanCreateView(context, ORDERS));
        checks.put("rename view", context -> control.checkCanRenameView(context, ORDERS, RENAMED));
        checks.put("set view authorization", context -> control.checkCanSetViewAuthorization(context, ORDERS, CAROL));
        checks.put("drop view", context -> control.checkCanDropView(context, ORDERS));
        checks.put("create view with select", context -> control.checkCanCreateViewWithSelectFromColumns(context, ORDERS, Set.of("id")));
        checks.put("create materialized view", context -> control.checkCanCreateMaterializedView(context, ORDERS, Map.of()));
        checks.put("refresh materialized view", context -> control.checkCanRefreshMaterializedView(context, ORDERS));
        checks.put("set materialized view properties", context -> control.checkCanSetMaterializedViewProperties(context, ORDERS, Map.of()));
        checks.put("drop materialized view", context -> control.checkCanDropMaterializedView(context, ORDERS));
        checks.put("rename materialized view", context -> control.checkCanRenameMaterializedView(context, ORDERS, RENAMED));
        checks.put("execute table procedure", context -> control.checkCanExecuteTableProcedure(context, ORDERS, "optimize"));
        checks.put("create function", context -> control.checkCanCreateFunction(context, CLEAN));
        checks.put("drop function", context -> control.checkCanDropFunction(context, CLEAN));
        checks.put("show create function", context -> control.checkCanShowCreateFunction(context, CLEAN));
        checks.put("call procedure", context -> control.checkCanExecuteProcedure(context, CLEAN));
        checks.put("set catalog session property", context -> control.checkCanSetCatalogSessionProperty(context, "hive", "compression_codec"));
        checks.put("set system session property", context -> control.checkCanSetSystemSessionProperty(context.getIdentity(), context.getQueryId(),
                "query_max_run_time"));
        checks.put("impersonate user", context -> control.checkCanImpersonateUser(context.getIdentity(), "carol"));
        checks.put("view query", context -> control.checkCanViewQueryOwnedBy(context.getIdentity(), Identity.ofUser("carol")));
        checks.put("kill query", context -> control.checkCanKillQueryOwnedBy(context.getIdentity(), Identity.ofUser("carol")));
        checks.put("read system information", context -> control.checkCanReadSystemInformation(context.getIdentity()));
        checks.put("write system information", context -> control.checkCanWriteSystemInformation(context.getIdentity()));
        return checks;
    }

    @Test
    void allowsWhatThePoliciesAllow()
    {
        checks(control).forEach((name, check) -> assertThatCode(() -> check.accept(context(ALICE))).as(name).doesNotThrowAnyException());
        assertThat(control.canAccessCatalog(context(ALICE), "hive")).isTrue();
        assertThat(control.canExecuteFunction(context(ALICE), CLEAN)).isTrue();
        assertThat(control.canCreateViewWithExecuteFunction(context(ALICE), CLEAN)).isTrue();
        assertThat(test.events()).isNotEmpty().allSatisfy(event -> assertThat(EventFields.of(event)).containsEntry("outcome", "ALLOWED"));
    }

    @Test
    void refusesWhatNoPolicyAllows()
    {
        checks(control).forEach((name, check) -> assertThatThrownBy(() -> check.accept(context(BOB))).as(name)
                .isInstanceOf(AccessDeniedException.class).hasMessageStartingWith("Access Denied: bob may not "));
        assertThat(control.canAccessCatalog(context(BOB), "hive")).isFalse();
        assertThat(control.canExecuteFunction(context(BOB), CLEAN)).isFalse();
        assertThat(control.canCreateViewWithExecuteFunction(context(BOB), CLEAN)).isFalse();
    }

    @Test
    void reportsWhoAskedInWhichQuery()
    {
        control.checkCanInsertIntoTable(context(ALICE), ORDERS);
        assertThat(EventFields.of(test.events().get(0))).containsEntry("user", "alice").containsEntry("resource", "hive.sales.orders")
                .containsEntry("accessType", "insert").containsEntry("action", "insert").containsEntry("request", "query 20261019_000001_00000_abcde");
        assertThatThrownBy(() -> control.checkCanSelectFromColumns(context(ALICE), ORDERS, Set.of("id", "card")))
                .hasMessage("Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 2 denies it");
    }

    @Test
    void letsEveryoneRunQueriesAndSeeAndKillTheirOwn()
    {
        assertThatCode(() -> {
            control.checkCanExecuteQuery(BOB, new QueryId("q"));
            control.checkCanSetUser(Optional.empty(), "bob");
            control.checkCanViewQueryOwnedBy(BOB, BOB);
            control.checkCanKillQueryOwnedBy(BOB, BOB);
            control.checkCanShowRoles(context(BOB));
            control.checkCanShowCurrentRoles(context(BOB));
            control.checkCanShowRoleGrants(context(BOB));
        }).doesNotThrowAnyException();
        assertThat(control.filterViewQueryOwnedBy(ALICE, List.of(ALICE, BOB, Identity.ofUser("carol")))).extracting(Identity::getUser)
                .containsExactly("alice", "carol");
    }

    @Test
    void refusesTrinosOwnPrivilegesAndRoles()
    {
        SystemSecurityContext context = context(ALICE);
        EntityKindAndName entity = new EntityKindAndName("TABLE", List.of("hive", "sales", "orders"));
        List<ThrowingConsumer<SystemSecurityContext>> statements = List.of(
                c -> control.checkCanGrantSchemaPrivilege(c, Privilege.SELECT, SALES, CAROL, false),
                c -> control.checkCanDenySchemaPrivilege(c, Privilege.SELECT, SALES, CAROL),
                c -> control.checkCanRevokeSchemaPrivilege(c, Privilege.SELECT, SALES, CAROL, false),
                c -> control.checkCanGrantTablePrivilege(c, Privilege.SELECT, ORDERS, CAROL, true),
                c -> control.checkCanDenyTablePrivilege(c, Privilege.SELECT, ORDERS, CAROL),
                c -> control.checkCanRevokeTablePrivilege(c, Privilege.SELECT, ORDERS, CAROL, false),
                c -> control.checkCanGrantEntityPrivilege(c, new EntityPrivilege("SELECT"), entity, CAROL, false),
                c -> control.checkCanDenyEntityPrivilege(c, new EntityPrivilege("SELECT"), entity, CAROL),
                c -> control.checkCanRevokeEntityPrivilege(c, new EntityPrivilege("SELECT"), entity, CAROL, false),
                c -> control.checkCanCreateRole(c, "analyst", Optional.empty()),
                c -> control.checkCanDropRole(c, "analyst"),
                c -> control.checkCanGrantRoles(c, Set.of("analyst"), Set.of(CAROL), false, Optional.empty()),
                c -> control.checkCanRevokeRoles(c, Set.of("analyst"), Set.of(CAROL), false, Optional.empty()));
        for (ThrowingConsumer<SystemSecurityContext> statement : statements) {
            assertThatThrownBy(() -> statement.accept(context)).isInstanceOf(AccessDeniedException.class).hasMessageEndingWith(TrinoAccess.MANAGED);
        }
    }

    @Test
    void listsWhatAUserMayUse()
    {
        SystemSecurityContext alice = context(ALICE);
        SystemSecurityContext bob = context(BOB);
        assertThat(control.filterCatalogs(alice, new LinkedHashSet<>(List.of("hive", "iceberg")))).containsExactly("hive");
        assertThat(control.filterSchemas(bob, "hive", new LinkedHashSet<>(List.of("sales", "information_schema")))).containsExactly("information_schema");
        assertThat(control.filterTables(alice, "hive", Set.of(new SchemaTableName("sales", "orders")))).hasSize(1);
        assertThat(control.filterTables(bob, "hive", Set.of(new SchemaTableName("sales", "orders")))).isEmpty();
        assertThat(control.filterColumns(bob, ORDERS, Set.of("id"))).isEmpty();
        assertThat(control.filterColumns(alice, ORDERS, new LinkedHashSet<>(List.of("id", "card")))).containsExactly("id", "card");
        assertThat(control.filterFunctions(alice, "hive", Set.of(new SchemaFunctionName("udf", "clean")))).hasSize(1);
        assertThat(control.filterFunctions(bob, "hive", Set.of(new SchemaFunctionName("udf", "clean")))).isEmpty();
    }

    @Test
    void filtersRowsAndMasksColumnsOfTheirType()
    {
        List<ViewExpression> filters = control.getRowFilters(context(ALICE), ORDERS);
        assertThat(filters).singleElement().satisfies(filter -> {
            assertThat(filter.getExpression()).isEqualTo("region = 'EU'");
            assertThat(filter.getCatalog()).contains("hive");
            assertThat(filter.getSchema()).contains("sales");
            assertThat(filter.getSecurityIdentity()).isEmpty();
        });
        assertThat(control.getRowFilters(context(BOB), ORDERS)).isEmpty();
        ColumnSchema customer = ColumnSchema.builder().setName("customer").setType(VarcharType.createVarcharType(20)).build();
        ColumnSchema amount = ColumnSchema.builder().setName("amount").setType(BigintType.BIGINT).build();
        Map<ColumnSchema, ViewExpression> masks = control.getColumnMasks(context(ALICE), ORDERS, List.of(customer, amount));
        assertThat(masks).containsOnlyKeys(customer);
        assertThat(requireNonNull(masks.get(customer), "mask").getExpression()).isEqualTo("CAST((to_hex(sha256(to_utf8(\"customer\")))) AS varchar(20))");
    }

    @Test
    void stopsTheAgentWithTheCoordinator()
    {
        assertThat(control).hasToString("GrantForge");
        assertThat(control.access().runtime()).isSameAs(test.runtime());
        control.shutdown();
        assertThat(test.runtime().closed()).isTrue();
    }
}
