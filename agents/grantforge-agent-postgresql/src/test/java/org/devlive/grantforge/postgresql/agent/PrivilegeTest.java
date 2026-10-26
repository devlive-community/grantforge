// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PrivilegeTest
{
    private static final TableRef ORDERS = new TableRef("crm", "orders");

    @Test
    void mapsAccessTypesToPrivilegesAndBack()
    {
        assertThat(Privilege.keyword("select")).isEqualTo("SELECT");
        assertThat(Privilege.keyword("trigger")).isEqualTo("TRIGGER");
        assertThat(Privilege.keyword("create_schema")).isEqualTo("CREATE");
        assertThat(Privilege.keyword("temporary")).isEqualTo("TEMPORARY");
        assertThatIllegalArgumentException().isThrownBy(() -> Privilege.keyword("alter")).withMessageContaining("alter");

        assertThat(Privilege.accessType(Privilege.Kind.DATABASE, "CREATE")).isEqualTo("create_schema");
        assertThat(Privilege.accessType(Privilege.Kind.DATABASE, "TEMPORARY")).isEqualTo("temporary");
        assertThat(Privilege.accessType(Privilege.Kind.DATABASE, "CONNECT")).isNull();
        assertThat(Privilege.accessType(Privilege.Kind.SCHEMA, "CREATE")).isEqualTo("create");
        assertThat(Privilege.accessType(Privilege.Kind.SCHEMA, "USAGE")).isNull();
        assertThat(Privilege.accessType(Privilege.Kind.TABLE, "DELETE")).isEqualTo("delete");
        assertThat(Privilege.accessType(Privilege.Kind.COLUMN, "UPDATE")).isEqualTo("update");
        assertThat(Privilege.accessType(Privilege.Kind.TABLE, "REFERENCES")).isNull();
        assertThat(Privilege.accessType(Privilege.Kind.SEQUENCE, "USAGE")).isNull();
    }

    @Test
    void grantsOneStatementPerObjectWithColumnsListedByPrivilege()
    {
        List<String> statements = Privilege.statements(true, "grantforge_alice", List.of(Privilege.column(ORDERS, "region", "SELECT"),
                Privilege.column(ORDERS, "id", "SELECT"), Privilege.column(ORDERS, "id", "UPDATE"), Privilege.table(ORDERS, "DELETE"),
                Privilege.table(ORDERS, "INSERT"), Privilege.schema("crm", "USAGE"), Privilege.database("sales", "CONNECT"),
                Privilege.sequence(new TableRef("crm", "orders_id_seq"), "USAGE")));
        assertThat(statements).containsExactly("GRANT CONNECT ON DATABASE \"sales\" TO \"grantforge_alice\"",
                "GRANT USAGE ON SCHEMA \"crm\" TO \"grantforge_alice\"",
                "GRANT DELETE, INSERT, SELECT (\"id\", \"region\"), UPDATE (\"id\") ON TABLE \"crm\".\"orders\" TO \"grantforge_alice\"",
                "GRANT USAGE ON SEQUENCE \"crm\".\"orders_id_seq\" TO \"grantforge_alice\"");
        assertThat(Privilege.statements(false, "r", List.of(Privilege.table(ORDERS, "SELECT"))))
                .containsExactly("REVOKE SELECT ON TABLE \"crm\".\"orders\" FROM \"r\"");
        assertThat(Privilege.statements(true, "r", List.of())).isEmpty();
    }

    @Test
    void namesItselfInMessages()
    {
        assertThat(Privilege.column(ORDERS, "card", "SELECT")).hasToString("SELECT (card) ON TABLE crm.orders");
        assertThat(Privilege.database("sales", "TEMPORARY")).hasToString("TEMPORARY ON DATABASE sales");
        assertThat(Privilege.schema("crm", "CREATE")).hasToString("CREATE ON SCHEMA crm");
    }
}
