package org.devlive.grantforge.service.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LegacyDatabaseMigrationStrategy implements FlywayMigrationStrategy
{
    private static final Set<String> LEGACY_TABLES = new HashSet<>(Arrays.asList(
            "authx_menu", "authx_menu_method_relation", "authx_method", "authx_role",
            "authx_role_menu_relation", "authx_user", "authx_user_role_relation",
            "icon", "icon_type", "icon_type_icon_relation", "icon_usage", "icon_usage_icon_relation",
            "system_log", "system_log_type", "system_log_type_relation", "system_log_users_relation",
            "system_menu_icon_relation", "system_menu_type", "system_menu_type_relation"));

    @Override
    public void migrate(Flyway flyway)
    {
        boolean legacy = false;
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            String schema = flyway.getConfiguration().getDefaultSchema();
            if (schema == null) {
                schema = connection.getCatalog();
            }
            if (schema == null) {
                throw new FlywayException("Select a database in the datasource URL before migrating");
            }
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> tables = new HashSet<>();
            try (ResultSet result = metadata.getTables(schema, null, "%", new String[]{"TABLE"})) {
                while (result.next()) {
                    tables.add(result.getString("TABLE_NAME"));
                }
            }
            if (!tables.isEmpty() && !tables.contains(flyway.getConfiguration().getTable())) {
                if (!tables.containsAll(LEGACY_TABLES)) {
                    throw new FlywayException("Non-empty database is not a recognized AuthX schema; refusing automatic baseline");
                }
                for (String table : tables) {
                    if (table.startsWith("grantforge_")) {
                        throw new FlywayException("AuthX and GrantForge tables coexist; resolve the naming conflict before migration");
                    }
                }
                validateLegacyIdentities(connection, schema);
                legacy = true;
            }
        }
        catch (SQLException e) {
            throw new FlywayException("Unable to inspect the database before migration", e);
        }
        if (legacy) {
            Flyway.configure().configuration(flyway.getConfiguration())
                    .baselineVersion("1")
                    .baselineDescription("Existing AuthX database")
                    .load()
                    .baseline();
        }
        flyway.migrate();
    }

    private void validateLegacyIdentities(Connection connection, String schema) throws SQLException
    {
        for (String table : Arrays.asList("icon", "icon_type", "icon_usage", "system_log", "system_log_type", "system_menu_type")) {
            String qualified = "`" + schema.replace("`", "``") + "`.`" + table + "`";
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT id), MIN(id) FROM " + qualified)) {
                result.next();
                long rows = result.getLong(1);
                if (rows != result.getLong(2) || (rows > 0 && result.getLong(3) <= 0)) {
                    throw new FlywayException("Legacy table " + table + " has invalid or duplicate IDs; resolve them before migration");
                }
            }
        }
    }
}
