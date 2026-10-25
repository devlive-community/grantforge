// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
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
    private static final Inventory INVENTORY = new Inventory(Map.of("sales", List.of("orders"), "hr", List.of("salaries", "people")),
            database -> Map.of());
    private static final Snapshot POLICIES = new ClickHouseSnapshots()
            .allow(List.of("dave"), List.of("select"), "database", "hr", "table", "*", "column", "*")
            .deny(List.of("dave"), List.of("select"), "database", "hr", "table", "salaries", "column", "*")
            .deny(List.of("dave"), List.of("insert"), "database", "sales", "table", "orders", "column", "card")
            .deny(List.of("dave"), List.of("drop_database"), "database", "hr")
            .deny(List.of("dave"), List.of("system"), "server", "*").snapshot();

    private final Logger logger = Logger.getLogger(NativeConflicts.class.getName());
    private final List<String> warnings = new CopyOnWriteArrayList<>();
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
    void stop()
    {
        logger.removeHandler(capture);
    }

    private static ServerState state(List<ServerState.NativeGrant> grants)
    {
        Account dave = new Account("dave", Set.of("ops"), Set.of(), true, List.of(), List.of());
        return new ServerState("grantforge", List.of(dave), List.of(), Set.of(), Map.of(), Map.of(), grants, INVENTORY);
    }

    @Test
    void findsGrantsOutsideGrantForgeThatGiveWhatAPolicyDenies()
    {
        List<NativeConflicts.Conflict> found = new NativeConflicts().check(state(List.of(
                new ServerState.NativeGrant("dave", null, "SELECT", "hr", "salaries", null),
                new ServerState.NativeGrant(null, "ops", "SELECT", "hr", null, null),
                new ServerState.NativeGrant("dave", null, "INSERT", "sales", "orders", null),
                new ServerState.NativeGrant("dave", null, "DROP", null, null, null),
                new ServerState.NativeGrant("dave", null, "SYSTEM", null, null, null),
                new ServerState.NativeGrant("dave", null, "SYSTEM", "hr", null, null),
                new ServerState.NativeGrant("dave", null, "SELECT", "hr", "people", null),
                new ServerState.NativeGrant("dave", null, "INSERT", "sales", "orders", "id"),
                new ServerState.NativeGrant("dave", null, "SHOW TABLES", null, null, null),
                new ServerState.NativeGrant("eve", null, "SELECT", "hr", "salaries", null),
                new ServerState.NativeGrant(null, "nobody", "SELECT", "hr", null, null),
                new ServerState.NativeGrant(null, null, "SELECT", "hr", null, null))), POLICIES, Instant.now());
        assertThat(found).extracting(NativeConflicts.Conflict::grant, NativeConflicts.Conflict::resource, NativeConflicts.Conflict::through)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SELECT ON hr.salaries", "hr.salaries", null),
                        org.assertj.core.groups.Tuple.tuple("INSERT ON sales.orders", "sales.orders", null),
                        org.assertj.core.groups.Tuple.tuple("DROP ON *.*", "hr", null),
                        org.assertj.core.groups.Tuple.tuple("SYSTEM ON *.*", "*", null),
                        org.assertj.core.groups.Tuple.tuple("SELECT ON hr.*", "hr.salaries", "ops"));
        assertThat(found.get(0).policyId()).isEqualTo(2L);
        assertThat(found.get(1).policyId()).as("policies deny one column of it").isNull();
        assertThat(found.get(0).message()).isEqualTo("ClickHouse user dave holds SELECT ON hr.salaries directly, outside GrantForge, which gives"
                + " select on hr.salaries although GrantForge policy 2 denies it; the agent cannot take it away");
        assertThat(found.get(1).message()).contains("GrantForge policies deny part of it");
        assertThat(found.get(4).message()).contains("through role ops");
    }

    @Test
    void warnsOfEachConflictOnceWhileItLasts()
    {
        NativeConflicts conflicts = new NativeConflicts();
        ServerState conflicting = state(List.of(new ServerState.NativeGrant("dave", null, "SELECT", "hr", "salaries", null)));
        conflicts.check(conflicting, POLICIES, Instant.now());
        conflicts.check(conflicting, POLICIES, Instant.now());
        assertThat(warnings).hasSize(1);
        conflicts.check(state(List.of()), POLICIES, Instant.now());
        conflicts.check(conflicting, POLICIES, Instant.now());
        assertThat(warnings).as("gone and back").hasSize(2);
    }

    @Test
    void summarizesWhatItDoesNotReportOneByOne()
    {
        List<ServerState.NativeGrant> grants = new ArrayList<>();
        for (int index = 0; index < NativeConflicts.MAX_REPORTED + 3; index++) {
            grants.add(new ServerState.NativeGrant("dave", null, "SELECT", "hr", "salaries", "c" + index));
        }
        Snapshot columns = new ClickHouseSnapshots().deny(List.of("dave"), List.of("select"), "database", "hr", "table", "*", "column", "*").snapshot();
        new NativeConflicts().check(state(grants), columns, Instant.now());
        assertThat(warnings).hasSize(NativeConflicts.MAX_REPORTED + 1).last().asString().startsWith("3 more grants");
    }
}
