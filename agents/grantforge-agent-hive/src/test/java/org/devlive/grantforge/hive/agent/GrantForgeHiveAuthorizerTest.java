// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.security.HiveAuthenticationProvider;
import org.apache.hadoop.hive.ql.security.authorization.plugin.DisallowTransformHook;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAccessControlException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthorizer;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzContext;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzPluginException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzSessionContext;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveOperationType;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrincipal;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject.HivePrivObjectActionType;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject.HivePrivilegeObjectType;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.devlive.grantforge.hive.agent.HiveAccessTest.database;
import static org.devlive.grantforge.hive.agent.HiveAccessTest.object;
import static org.devlive.grantforge.hive.agent.HiveAccessTest.table;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrantForgeHiveAuthorizerTest
{
    private static final List<String> ALICE = List.of("alice");

    private final AtomicReference<Policies> policies = new AtomicReference<>();
    private final List<AccessEvent> events = new ArrayList<>();
    private final HiveAuthenticationProvider authenticator = mock(HiveAuthenticationProvider.class);
    private final GrantForgeHiveAuthorizer authorizer = new GrantForgeHiveAuthorizer(policies::get, events::add, authenticator, session(
            HiveAuthzSessionContext.CLIENT_TYPE.HIVESERVER2));

    private static HiveAuthzSessionContext session(HiveAuthzSessionContext.CLIENT_TYPE type)
    {
        HiveAuthzSessionContext.Builder builder = new HiveAuthzSessionContext.Builder();
        builder.setClientType(type);
        builder.setSessionString("session-1");
        return builder.build();
    }

    private static HiveAuthzContext context(String command)
    {
        HiveAuthzContext.Builder builder = new HiveAuthzContext.Builder();
        builder.setUserIpAddress("10.0.0.7");
        builder.setCommandString(command);
        return builder.build();
    }

    @BeforeEach
    void signsInAlice()
    {
        when(authenticator.getUserName()).thenReturn("alice");
        when(authenticator.getGroupNames()).thenReturn(List.of("analysts"));
        policies.set(new HiveSnapshots()
                .allow(ALICE, List.of("select"), "database", "sales", "table", "orders", "column", "*")
                .allow(ALICE, List.of("update", "alter"), "database", "sales", "table", "items", "column", "*")
                .allow(List.of("@analysts"), List.of("select"), "database", "hr", "table", "people", "column", "*")
                .allow(ALICE, List.of("all"), "database", "scratch", "table", "*", "column", "*")
                .deny(ALICE, List.of("select"), "database", "sales", "table", "orders", "column", "card")
                .allow(ALICE, List.of("read"), "url", "hdfs://nn/landing")
                .allow(ALICE, List.of("update"), "database", "sales", "table", "orders", "column", "*")
                .mask(ALICE, "mask_hash", null, "database", "sales", "table", "orders", "column", "customer")
                .mask(ALICE, "mask_none", null, "database", "sales", "table", "orders", "column", "id")
                .filter(ALICE, "region = 'EU'", "database", "sales", "table", "items")
                .role("analysts", "alice").role("auditors", "alice")
                .policies());
    }

    private void check(HiveOperationType operation, List<HivePrivilegeObject> inputs, List<HivePrivilegeObject> outputs) throws Exception
    {
        authorizer.checkPrivileges(operation, inputs, outputs, context("statement"));
    }

    @Test
    void allowsWhatThePoliciesAllowAndReportsEachAccess() throws Exception
    {
        check(HiveOperationType.QUERY, List.of(table("sales", "orders", "id", "customer"), table("hr", "people")), List.of());
        assertThat(events).extracting(event -> EventFields.of(event).get("resource")).containsExactly("sales.orders.id", "sales.orders.customer",
                "hr.people");
        assertThat(EventFields.of(events.get(0))).containsEntry("user", "alice").containsEntry("clientIp", "10.0.0.7").containsEntry("outcome", "ALLOWED")
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("resourceType", "column").containsEntry("action", "QUERY")
                .containsEntry("request", "statement").containsEntry("accessType", "select");
        events.clear();
        check(HiveOperationType.QUERY, List.of(table("sales", "orders", "id"), table("sales", "orders", "id")), List.of());
        assertThat(events).as("a repeated object is checked once").hasSize(1);
        check(HiveOperationType.CREATETABLE, List.of(), List.of(database("scratch"), table("scratch", "copy")));
        check(HiveOperationType.LOAD, List.of(object(HivePrivilegeObjectType.DFS_URI, null, "hdfs://nn/landing/day=1", null,
                HivePrivObjectActionType.OTHER)), List.of(table("scratch", "copy")));
    }

    @Test
    void refusesWithTheFirstAccessThePoliciesDoNotAllow()
    {
        assertRefused(() -> check(HiveOperationType.QUERY, List.of(table("sales", "orders", "id", "card")), List.of()),
                "Permission denied: user [alice] does not have [SELECT] privilege on [sales.orders.card]");
        AccessEvent denied = events.get(events.size() - 1);
        assertThat(EventFields.of(denied)).containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", "5");
        assertRefused(() -> check(HiveOperationType.DROPTABLE, List.of(), List.of(table("sales", "orders"))),
                "does not have [DROP] privilege on [sales.orders]");
        assertThat(EventFields.of(events.get(events.size() - 1))).as("refused without a policy").containsEntry("policyId", null)
                .containsEntry("enforcer", "GRANTFORGE");
        assertRefused(() -> check(HiveOperationType.LOAD, List.of(object(HivePrivilegeObjectType.DFS_URI, null, "hdfs://nn/private", null,
                HivePrivObjectActionType.OTHER)), List.of()), "does not have [READ] privilege on [hdfs://nn/private]");
        assertRefused(() -> check(HiveOperationType.REPLDUMP, List.of(), List.of()), "does not have [REPLADMIN] privilege on [*]");
    }

    @Test
    void listingNeedsAnyAccessToTheDatabaseOrWhatIsInIt() throws Exception
    {
        check(HiveOperationType.SWITCHDATABASE, List.of(database("sales")), List.of());
        check(HiveOperationType.SWITCHDATABASE, List.of(database("hr")), List.of());
        assertRefused(() -> check(HiveOperationType.SWITCHDATABASE, List.of(database("finance")), List.of()),
                "does not have [USE] privilege on [finance]");
        check(HiveOperationType.SHOWDATABASES, List.of(), List.of());

        List<HivePrivilegeObject> listed = authorizer.filterListCmdObjects(List.of(database("sales"), database("finance"), database("scratch"),
                table("sales", "orders"), table("sales", "secrets"), object(HivePrivilegeObjectType.FUNCTION, "sales", "f", null, null),
                object(null, "sales", null, null, null), object(HivePrivilegeObjectType.DATABASE, null, null, null, null),
                object(HivePrivilegeObjectType.DATABASE, null, "hr", null, null)), context("show"));
        assertThat(listed).extracting(HivePrivilegeObject::getDbname, HivePrivilegeObject::getObjectName).containsExactly(
                org.assertj.core.groups.Tuple.tuple("sales", null), org.assertj.core.groups.Tuple.tuple("scratch", null),
                org.assertj.core.groups.Tuple.tuple("sales", "orders"), org.assertj.core.groups.Tuple.tuple(null, "hr"));
        assertThat(authorizer.filterListCmdObjects(null, null)).isEmpty();
        List<HivePrivilegeObject> none = List.of();
        assertThat(authorizer.filterListCmdObjects(none, null)).isSameAs(none);
    }

    @Test
    void masksColumnsAndFiltersRows() throws Exception
    {
        assertThat(authorizer.needTransform()).isTrue();
        HivePrivilegeObject orders = table("sales", "orders", "id", "customer", "odd`name");
        HivePrivilegeObject items = table("sales", "items", "sku");
        HivePrivilegeObject people = table("hr", "people", "name");
        HivePrivilegeObject function = object(HivePrivilegeObjectType.FUNCTION, "sales", "f", null, null);
        List<HivePrivilegeObject> changed = authorizer.applyRowFilterAndColumnMasking(context("select"), List.of(orders, items, people, function));
        assertThat(changed).containsExactly(orders, items);
        assertThat(orders.getCellValueTransformers()).containsExactly("`id`", "mask_hash(`customer`)", "`odd``name`");
        assertThat(orders.getRowFilterExpression()).isNull();
        assertThat(items.getRowFilterExpression()).isEqualTo("region = 'EU'");
        assertThat(items.getCellValueTransformers()).containsExactly("`sku`");
        assertThat(authorizer.applyRowFilterAndColumnMasking(null, null)).isEmpty();
        HivePrivilegeObject untyped = object(null, "sales", "items", null, null);
        assertThat(authorizer.applyRowFilterAndColumnMasking(null, List.of(untyped))).containsExactly(untyped);
    }

    @Test
    void masksWithoutAnExpressionShowNothing() throws Exception
    {
        policies.set(new HiveSnapshots().mask(ALICE, "custom", null, "database", "sales", "table", "orders", "column", "card")
                .mask(ALICE, "opaque", null, "database", "sales", "table", "orders", "column", "pin")
                .mask(ALICE, "custom", "concat('x', {col})", "database", "sales", "table", "orders", "column", "name").policies());
        HivePrivilegeObject orders = table("sales", "orders", "card", "pin", "name");
        authorizer.applyRowFilterAndColumnMasking(null, List.of(orders));
        assertThat(orders.getCellValueTransformers()).containsExactly("NULL", "NULL", "concat('x', `name`)");
    }

    @Test
    void writesAndExportsAreRefusedWhereTheUserSeesMaskedValuesOrFilteredRows() throws Exception
    {
        HivePrivilegeObject insert = object(HivePrivilegeObjectType.TABLE_OR_VIEW, "sales", "items", null, HivePrivObjectActionType.INSERT);
        assertRefused(() -> check(HiveOperationType.QUERY, List.of(), List.of(insert)),
                "does not have [UPDATE] privilege on [sales.items], whose values are masked or rows filtered for the user");
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("outcome", "DENIED");
        HivePrivilegeObject update = object(HivePrivilegeObjectType.TABLE_OR_VIEW, "sales", "orders", null, HivePrivObjectActionType.UPDATE);
        assertRefused(() -> check(HiveOperationType.QUERY, List.of(), List.of(update)), "whose values are masked");
        assertRefused(() -> check(HiveOperationType.EXPORT, List.of(table("sales", "orders", "id")), List.of()), "whose values are masked");
        policies.set(new HiveSnapshots().allow(ALICE, List.of("update"), "database", "sales", "table", "orders", "column", "*")
                .mask(ALICE, "mask_none", null, "database", "sales", "table", "orders", "column", "id").policies());
        check(HiveOperationType.QUERY, List.of(), List.of(update));
    }

    @Test
    void aServiceNotInUseLeavesHiveUnchecked() throws Exception
    {
        policies.set(new HiveSnapshots().disabled().policies());
        check(HiveOperationType.DROPDATABASE, List.of(), List.of(database("sales")));
        check(HiveOperationType.DFS, List.of(), List.of());
        List<HivePrivilegeObject> all = List.of(database("finance"));
        assertThat(authorizer.filterListCmdObjects(all, null)).isSameAs(all);
        assertThat(authorizer.applyRowFilterAndColumnMasking(null, List.of(table("sales", "orders", "card")))).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void withoutASnapshotOrAUserEverythingIsRefused() throws Exception
    {
        policies.set(Policies.of(null));
        assertRefused(() -> check(HiveOperationType.QUERY, List.of(table("sales", "orders")), List.of()), "Permission denied");
        assertThat(authorizer.filterListCmdObjects(List.of(database("sales")), null)).isEmpty();
        check(HiveOperationType.SHOWDATABASES, List.of(), List.of());
        when(authenticator.getUserName()).thenReturn("");
        assertRefused(() -> check(HiveOperationType.SHOWDATABASES, List.of(), List.of()), "user of the session is not known");
        assertThatThrownBy(() -> authorizer.applyRowFilterAndColumnMasking(null, List.of(table("sales", "orders"))))
                .isInstanceOf(SemanticException.class).hasMessageContaining("not known");
        when(authenticator.getUserName()).thenReturn(null);
        assertThatThrownBy(authorizer::getCurrentRoleNames).isInstanceOf(HiveAuthzPluginException.class);
    }

    @Test
    void dfsCommandsAndUnnamedObjectsAreRefused()
    {
        assertRefused(() -> check(HiveOperationType.DFS, List.of(object(HivePrivilegeObjectType.COMMAND_PARAMS, null, null, null, null)),
                List.of()), "does not have privilege for [DFS] command");
        assertThat(EventFields.of(events.get(0))).containsEntry("resource", "dfs").containsEntry("outcome", "DENIED");
        assertRefused(() -> check(HiveOperationType.QUERY, List.of(table("sales", "")), List.of()), "Hive names no table");
    }

    @Test
    void brokenPoliciesFailClosed() throws Exception
    {
        Policies broken = mock(Policies.class);
        when(broken.enforced()).thenReturn(true);
        when(broken.decide(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("corrupt"));
        when(broken.rowFilter(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("corrupt"));
        policies.set(broken);
        assertThatThrownBy(() -> check(HiveOperationType.QUERY, List.of(table("sales", "orders")), List.of()))
                .isInstanceOf(HiveAuthzPluginException.class).hasMessageContaining("cannot decide select on sales.orders: corrupt");
        assertThatThrownBy(() -> authorizer.filterListCmdObjects(List.of(database("sales")), null)).isInstanceOf(HiveAuthzPluginException.class);
        assertThatThrownBy(() -> authorizer.applyRowFilterAndColumnMasking(null, List.of(table("sales", "orders"))))
                .isInstanceOf(SemanticException.class).hasMessageContaining("cannot mask or filter sales.orders: corrupt");
        policies.set(null);
        assertThatThrownBy(() -> check(HiveOperationType.QUERY, List.of(), List.of())).isInstanceOf(HiveAuthzPluginException.class)
                .hasMessageContaining("policies are not available");
        assertThatThrownBy(() -> authorizer.applyRowFilterAndColumnMasking(null, List.of(table("sales", "orders"))))
                .isInstanceOf(SemanticException.class);
    }

    @Test
    void grantsAndRolesAreGrantForges() throws Exception
    {
        assertThat(authorizer.getVersion()).isEqualTo(HiveAuthorizer.VERSION.V1);
        assertThat(authorizer.getCurrentRoleNames()).containsExactly("analysts", "auditors");
        HivePrincipal bob = new HivePrincipal("bob", HivePrincipal.HivePrincipalType.USER);
        List<Executable> refused = List.of(() -> authorizer.grantPrivileges(List.of(), List.of(), database("sales"), bob, false),
                () -> authorizer.revokePrivileges(List.of(), List.of(), database("sales"), bob, false),
                () -> authorizer.createRole("r", bob), () -> authorizer.dropRole("r"), () -> authorizer.getPrincipalGrantInfoForRole("r"),
                () -> authorizer.getRoleGrantInfoForPrincipal(bob), () -> authorizer.grantRole(List.of(), List.of(), false, bob),
                () -> authorizer.revokeRole(List.of(), List.of(), false, bob), authorizer::getAllRoles,
                () -> authorizer.showPrivileges(bob, database("sales")), () -> authorizer.setCurrentRole("admin"));
        for (Executable call : refused) {
            assertThatThrownBy(call::execute).isInstanceOf(HiveAuthzPluginException.class).hasMessageContaining("managed in GrantForge");
        }
    }

    @Test
    void disallowsTransformScriptsAndUnsafeSettingsInHiveServer2()
    {
        HiveConf conf = new HiveConf();
        conf.set("hive.exec.pre.hooks", "");
        assertDoesNotThrow(() -> authorizer.applyAuthorizationConfigPolicy(conf));
        assertThat(conf.get("hive.exec.pre.hooks")).isEqualTo(DisallowTransformHook.class.getName());
        conf.set("hive.exec.pre.hooks", "custom.Hook");
        assertDoesNotThrow(() -> authorizer.applyAuthorizationConfigPolicy(conf));
        assertThat(conf.get("hive.exec.pre.hooks")).isEqualTo("custom.Hook," + DisallowTransformHook.class.getName());

        HiveConf cli = new HiveConf();
        cli.set("hive.exec.pre.hooks", "");
        assertDoesNotThrow(() -> new GrantForgeHiveAuthorizer(policies::get, events::add, authenticator,
                session(HiveAuthzSessionContext.CLIENT_TYPE.HIVECLI)).applyAuthorizationConfigPolicy(cli));
        assertDoesNotThrow(() -> new GrantForgeHiveAuthorizer(policies::get, events::add, authenticator, null).applyAuthorizationConfigPolicy(cli));
        assertThat(cli.get("hive.exec.pre.hooks")).isEmpty();
    }

    private static void assertRefused(Executable call, String message)
    {
        assertThatThrownBy(call::execute).isInstanceOf(HiveAccessControlException.class).hasMessageContaining(message);
    }
}
