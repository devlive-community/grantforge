// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PrivilegesTest
{
    @Test
    void grantsDorissOwnPrivilegeForEachAccessType()
    {
        assertThat(Privileges.privilege(Privileges.SELECT)).isEqualTo("SELECT_PRIV");
        assertThat(Privileges.privilege(Privileges.LOAD)).isEqualTo("LOAD_PRIV");
        assertThat(Privileges.privilege(Privileges.SHOW_VIEW)).isEqualTo("SHOW_VIEW_PRIV");
        assertThat(Privileges.privilege(Privileges.DROP_DATABASE)).isEqualTo("DROP_PRIV");
        assertThat(Privileges.privilege(Privileges.CREATE_DATABASE)).isEqualTo("CREATE_PRIV");
        assertThat(Privileges.privilege(Privileges.USAGE)).isEqualTo("USAGE_PRIV");
        assertThatIllegalArgumentException().isThrownBy(() -> Privileges.privilege("truncate")).withMessageContaining("truncate");
    }

    @Test
    void readsWhatAPrivilegeAmountsToOnItsLevel()
    {
        assertThat(Privileges.accessTypes("Select_priv", "table")).containsExactly(Privileges.SELECT);
        assertThat(Privileges.accessTypes("INSERT", "table")).containsExactly(Privileges.LOAD);
        assertThat(Privileges.accessTypes("Load_priv", "database")).containsExactly(Privileges.LOAD);
        assertThat(Privileges.accessTypes("Alter_priv", "table")).containsExactly(Privileges.ALTER);
        assertThat(Privileges.accessTypes("Show_view_priv", "table")).containsExactly(Privileges.SHOW_VIEW);
        assertThat(Privileges.accessTypes("SHOW VIEW", "table")).containsExactly(Privileges.SHOW_VIEW);
        assertThat(Privileges.accessTypes("Usage_priv", "workload_group")).containsExactly(Privileges.USAGE);
        assertThat(Privileges.accessTypes("Drop_priv", "table")).containsExactly(Privileges.DROP);
        assertThat(Privileges.accessTypes("Drop_priv", "database")).containsExactly(Privileges.DROP, Privileges.DROP_DATABASE);
        assertThat(Privileges.accessTypes("Create_priv", "database")).containsExactly(Privileges.CREATE);
        assertThat(Privileges.accessTypes("Create_priv", "catalog")).containsExactly(Privileges.CREATE, Privileges.CREATE_DATABASE);
        assertThat(Privileges.accessTypes("Admin_priv", "table")).hasSize(9);
        assertThat(Privileges.accessTypes("Node_priv", "table")).isEmpty();
        assertThat(Privileges.accessTypes("Grant_priv", "table")).isEmpty();
    }
}
