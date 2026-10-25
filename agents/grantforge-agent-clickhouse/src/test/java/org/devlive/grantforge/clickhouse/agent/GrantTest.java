// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrantTest
{
    @Test
    void writesThePrivilegesOfEachTarget()
    {
        assertThat(Grant.server("create_database").sql()).isEqualTo("CREATE DATABASE ON *.*");
        assertThat(Grant.database("create", "sales").sql()).isEqualTo("CREATE TABLE, CREATE VIEW, CREATE DICTIONARY ON \"sales\".*");
        assertThat(Grant.table("drop", new TableRef("sales", "orders"), List.of()).sql())
                .isEqualTo("DROP TABLE, DROP VIEW, DROP DICTIONARY ON \"sales\".\"orders\"");
        assertThat(Grant.table("select", new TableRef("sales", "orders"), List.of("id", "we ird")).sql())
                .isEqualTo("SELECT(\"id\", \"we ird\") ON \"sales\".\"orders\"");
    }

    @Test
    void sortsServerFirstThenDatabasesTablesTypesAndColumns()
    {
        List<Grant> grants = new ArrayList<>(List.of(Grant.table("select", new TableRef("a", "t"), List.of("y")),
                Grant.table("select", new TableRef("a", "t"), List.of("x")), Grant.table("alter", new TableRef("a", "t"), List.of()),
                Grant.database("select", "a"), Grant.server("system"), Grant.database("select", "b")));
        grants.sort(Grant.ORDER);
        assertThat(grants).extracting(Grant::sql).containsExactly("SYSTEM ON *.*", "SELECT ON \"a\".*", "ALTER ON \"a\".\"t\"",
                "SELECT(\"x\") ON \"a\".\"t\"", "SELECT(\"y\") ON \"a\".\"t\"", "SELECT ON \"b\".*");
    }

    @Test
    void copiesTheColumns()
    {
        List<String> columns = new ArrayList<>(List.of("id"));
        Grant grant = Grant.table("select", new TableRef("a", "t"), columns);
        columns.add("other");
        assertThat(grant.columns()).containsExactly("id");
    }
}
