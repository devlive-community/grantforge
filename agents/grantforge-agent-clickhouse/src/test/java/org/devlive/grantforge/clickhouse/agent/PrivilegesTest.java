// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PrivilegesTest
{
    @Test
    void namesWhatItGrantsForEachAccessType()
    {
        assertThat(Privileges.keywords("select")).containsExactly("SELECT");
        assertThat(Privileges.keywords("drop")).containsExactly("DROP TABLE", "DROP VIEW", "DROP DICTIONARY");
        assertThat(Privileges.keywords("kill_query")).containsExactly("KILL QUERY");
        assertThatIllegalArgumentException().isThrownBy(() -> Privileges.keywords("fly")).withMessageContaining("fly");
    }

    @Test
    void readsWhatAPrivilegeAmountsTo()
    {
        assertThat(Privileges.accessTypes("ALL")).hasSize(11);
        assertThat(Privileges.accessTypes(" select ")).containsExactly("select");
        assertThat(Privileges.accessTypes("INSERT")).containsExactly("insert");
        assertThat(Privileges.accessTypes("TRUNCATE")).containsExactly("truncate");
        assertThat(Privileges.accessTypes("OPTIMIZE")).containsExactly("optimize");
        assertThat(Privileges.accessTypes("ALTER")).containsExactly("alter");
        assertThat(Privileges.accessTypes("ALTER UPDATE")).containsExactly("alter");
        assertThat(Privileges.accessTypes("ALTER SETTINGS")).as("a table's settings").containsExactly("alter");
        assertThat(Privileges.accessTypes("ALTER USER")).isEmpty();
        assertThat(Privileges.accessTypes("ALTER ROW POLICY")).isEmpty();
        assertThat(Privileges.accessTypes("ALTER NAMED COLLECTION")).isEmpty();
        assertThat(Privileges.accessTypes("DROP")).containsExactly("drop", "drop_database");
        assertThat(Privileges.accessTypes("DROP VIEW")).containsExactly("drop");
        assertThat(Privileges.accessTypes("DROP DATABASE")).containsExactly("drop_database");
        assertThat(Privileges.accessTypes("CREATE")).containsExactly("create", "create_database");
        assertThat(Privileges.accessTypes("CREATE TEMPORARY TABLE")).containsExactly("create");
        assertThat(Privileges.accessTypes("CREATE DATABASE")).containsExactly("create_database");
        assertThat(Privileges.accessTypes("KILL QUERY")).containsExactly("kill_query");
        assertThat(Privileges.accessTypes("SYSTEM")).containsExactly("system");
        assertThat(Privileges.accessTypes("SYSTEM FLUSH LOGS")).containsExactly("system");
        assertThat(Privileges.accessTypes("SHOW TABLES")).isEmpty();
    }
}
