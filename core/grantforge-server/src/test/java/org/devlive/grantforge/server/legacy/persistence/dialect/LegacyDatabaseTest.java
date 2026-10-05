// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy.persistence.dialect;

import org.devlive.grantforge.server.legacy.LegacyData;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LegacyDatabaseTest
{
    private static LegacyData read(String database, String... statements) throws SQLException
    {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + database + ";MODE=MySQL");
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            return LegacyDatabase.read(connection);
        }
    }

    @Test
    void readsAnAuthxDatabase() throws SQLException
    {
        LegacyData data = read("legacy-authx", "RUNSCRIPT FROM 'classpath:/legacy/authx.sql' CHARSET 'UTF-8'");

        assertThat(data.users()).hasSize(6);
        assertThat(data.users().get(0)).isEqualTo(new LegacyData.User(1, "系统用户", null, false, true, true, "0"));
        assertThat(data.roles()).extracting(LegacyData.Role::code).containsExactly("GLY", "PT YH", null, "gly", "tenant-admin");
        // A parent of 0 is the top level.
        assertThat(data.menus().get(0)).isEqualTo(new LegacyData.Menu(2, "系统菜单", "#", 2, null, null, true));
        assertThat(data.methods()).extracting(LegacyData.Method::method).containsExactly("GET", "post", "TRACE");
        // A link with an empty side is dropped.
        assertThat(data.userRoles()).hasSize(4).doesNotContain(new LegacyData.Link(2, 0));
        assertThat(data.roleMenus()).hasSize(6);
        assertThat(data.menuMethods()).hasSize(4);
    }

    @Test
    void readsTheRenamedTablesWithDefaultFlags() throws SQLException
    {
        LegacyData data = read("legacy-renamed",
                "CREATE TABLE grantforge_user (id INT, name VARCHAR(100), password VARCHAR(200), active TINYINT, locked TINYINT,"
                        + " is_system TINYINT, email VARCHAR(100))",
                "CREATE TABLE grantforge_role (id INT, name VARCHAR(100), code VARCHAR(50), description VARCHAR(100), active TINYINT)",
                "CREATE TABLE grantforge_menu (id INT, name VARCHAR(100), url VARCHAR(200), sorted INT, parent INT,"
                        + " description VARCHAR(100), active TINYINT)",
                "CREATE TABLE grantforge_method (id INT, method VARCHAR(200))",
                "CREATE TABLE grantforge_user_role_relation (user_id INT, role_id INT)",
                "CREATE TABLE grantforge_role_menu_relation (role_id INT, menu_id INT)",
                "CREATE TABLE grantforge_menu_method_relation (menu_id INT, method_id INT)",
                "INSERT INTO grantforge_user (id, name) VALUES (7, 'someone')",
                "INSERT INTO grantforge_menu (id, name, parent) VALUES (8, 'menu', NULL)");

        assertThat(data.users()).containsExactly(new LegacyData.User(7, "someone", null, true, false, false, null));
        assertThat(data.menus()).containsExactly(new LegacyData.Menu(8, "menu", null, 0, null, null, true));
        assertThat(data.roles()).isEmpty();
    }

    @Test
    void refusesAnotherDatabase()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> read("legacy-none", "CREATE TABLE something (id INT)"))
                .withMessageContaining("authx_user");
    }

    @Test
    void describesUrlsWithoutParameters()
    {
        assertThat(LegacyDatabase.describe("jdbc:mysql://db:3306/authx?user=root&password=secret")).isEqualTo("jdbc:mysql://db:3306/authx");
        assertThat(LegacyDatabase.describe("jdbc:h2:mem:old;PASSWORD=x")).isEqualTo("jdbc:h2:mem:old");
        assertThat(LegacyDatabase.describe(null)).isEmpty();
    }
}
