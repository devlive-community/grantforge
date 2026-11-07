// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class NativeConflictsTest
{
    private static final Inventory INVENTORY = new Inventory("rs0", List.of("sales", "hr"), database -> "sales".equals(database) ? List.of("orders", "items") : List.of("salaries"));
    private static final Account.RoleRef ROLE = new Account.RoleRef("custom", "admin");

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getLogger(NativeConflicts.class.getName());
    private final Handler capture = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            warnings.add(record.getMessage());
        }

        @Override
        public void flush()
        {
            // Nothing is buffered.
        }

        @Override
        public void close()
        {
            // Nothing to let go of.
        }
    };

    @BeforeEach
    void listen()
    {
        logger.addHandler(capture);
    }

    @AfterEach
    void stopListening()
    {
        logger.removeHandler(capture);
    }

    private static ServerState state(ServerState.Role role, Map<String, Account.RoleRef> superusers, Boolean authorization)
    {
        Account dave = new Account("dave", List.of(new Account.Identity("dave", "admin", List.of(ROLE))), Set.of(), Set.of(ROLE));
        return new ServerState(List.of(dave), superusers, Map.of(), Map.of(ROLE, role), INVENTORY, authorization);
    }

    private static ServerState.Role role(Target target, String... actions)
    {
        return new ServerState.Role(Map.of(target, Set.of(actions)), false);
    }

    private static Snapshot denying(String type, String... resource)
    {
        return new MongoSnapshots().deny(List.of("dave"), List.of(type), resource).snapshot();
    }

    private void check(ServerState state, Snapshot snapshot)
    {
        new NativeConflicts().check(state, snapshot, Instant.now());
    }

    @Test
    void warnsOfARoleThatGivesWhatAPolicyDeniesOnACollection()
    {
        check(state(role(Target.collection("sales", "orders"), "find", "listIndexes"), Map.of(), true), denying("read", "database", "sales", "collection", "orders"));
        assertThat(warnings).singleElement().asString().contains("MongoDB role custom@admin, which user dave has outside GrantForge, gives read on sales.orders");
    }

    @Test
    void followsAPrivilegeOnEveryCollectionOrDatabase()
    {
        check(state(role(new Target("", ""), "insert"), Map.of(), true), denying("insert", "database", "hr", "collection", "salaries"));
        assertThat(warnings).singleElement().asString().contains("gives insert on hr.salaries");
        warnings.clear();
        check(state(role(Target.database("sales"), "remove"), Map.of(), true), denying("remove", "database", "sales", "collection", "items"));
        assertThat(warnings).singleElement().asString().contains("gives remove on sales.items");
        warnings.clear();
        check(state(role(new Target("", "items"), "update"), Map.of(), true), denying("update", "database", "sales", "collection", "items"));
        assertThat(warnings).singleElement().asString().contains("gives update on sales.items");
    }

    @Test
    void warnsOfAPrivilegeOnADatabaseAsSuch()
    {
        check(state(role(Target.database("sales"), "dropDatabase", "createCollection"), Map.of(), true), denying("drop_database", "database", "sales"));
        assertThat(warnings).singleElement().asString().contains("gives drop_database on sales.*");
    }

    @Test
    void warnsOfAPrivilegeOnTheDeployment()
    {
        check(state(role(Target.DEPLOYMENT, "listDatabases", "shutdown"), Map.of(), true), denying("list_databases", "deployment", "rs0"));
        assertThat(warnings).singleElement().asString().contains("gives list_databases on the deployment");
    }

    @Test
    void saysNothingOfWhatNoPolicyDenies()
    {
        check(state(role(Target.database("sales"), "find", "shutdown"), Map.of(), true), new MongoSnapshots().snapshot());
        check(state(role(Target.collection("hr", "other"), "find"), Map.of(), true), denying("read", "database", "hr", "collection", "salaries"));
        assertThat(warnings).isEmpty();
    }

    @Test
    void warnsOfUsersWhoMayAdministerUsers()
    {
        check(state(role(Target.database("hr"), "grantRole"), Map.of(), true), new MongoSnapshots().snapshot());
        assertThat(warnings).singleElement().asString().contains("lets it administer users");
    }

    @Test
    void warnsOfUsersNoRoleBinds()
    {
        check(state(role(Target.database("sales")), Map.of("boss", new Account.RoleRef("root", "admin")), true), new MongoSnapshots().snapshot());
        assertThat(warnings).singleElement().asString().contains("MongoDB user boss has the role root@admin, which no role binds");
    }

    @Test
    void warnsOfAServerThatChecksNoAuthorizationOnce()
    {
        NativeConflicts conflicts = new NativeConflicts();
        Snapshot snapshot = new MongoSnapshots().snapshot();
        conflicts.check(state(role(Target.database("sales")), Map.of(), false), snapshot, Instant.now());
        conflicts.check(state(role(Target.database("sales")), Map.of(), false), snapshot, Instant.now());
        assertThat(warnings).singleElement().asString().contains("MongoDB checks no authorization");
    }

    @Test
    void aRoleItCouldNotReadIsPassedOver()
    {
        Account dave = new Account("dave", List.of(), Set.of(), Set.of(ROLE));
        check(new ServerState(List.of(dave), Map.of(), Map.of(), Map.of(), INVENTORY, null), new MongoSnapshots().snapshot());
        assertThat(warnings).isEmpty();
    }
}
