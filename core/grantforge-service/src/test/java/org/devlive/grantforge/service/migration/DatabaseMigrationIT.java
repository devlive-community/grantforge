package org.devlive.grantforge.service.migration;

import org.devlive.grantforge.service.MySQLConfigure;
import org.devlive.grantforge.service.repository.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseMigrationIT
{
    private String schema;
    private String databaseUrl;
    private String username;
    private String password;
    private Path migrations;
    private int migrationCount;

    @Before
    public void createIsolatedDatabase() throws Exception
    {
        String baseUrl = requiredEnvironment("GRANTFORGE_TEST_DB_URL");
        username = requiredEnvironment("GRANTFORGE_TEST_DB_USER");
        password = requiredEnvironment("GRANTFORGE_TEST_DB_PASSWORD");
        schema = "grantforge_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(baseUrl, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        int catalog = baseUrl.indexOf('/', "jdbc:mysql://".length());
        int query = baseUrl.indexOf('?');
        databaseUrl = baseUrl.substring(0, catalog + 1) + schema + (query < 0 ? "" : baseUrl.substring(query));
        migrationCount = flyway().info().all().length;
    }

    @After
    public void removeIsolatedDatabase() throws Exception
    {
        if (databaseUrl != null) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
        if (migrations != null) {
            try (Stream<Path> files = Files.walk(migrations)) {
                for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    @Test
    public void emptyDatabaseInitializesAndRepeatedStartupPreservesData() throws Exception
    {
        migrate(flyway());
        assertFalse(tableExists("authx_user"));
        assertTrue(tableExists("grantforge_user"));
        assertTrue(tableExists("system_settings"));
        assertTrue(tableExists("table_row"));
        execute("INSERT INTO icon_type (name, active) VALUES ('generated-id-test', 1)");
        assertEquals(1, number("SELECT COUNT(*) FROM icon_type WHERE name = 'generated-id-test' AND id > 0"));
        assertEquals(migrationCount, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        execute("INSERT INTO grantforge_user (id, name, password, active) VALUES (9001, 'kept-user', 'kept-hash', 1)");
        migrate(flyway());
        assertEquals(1, number("SELECT COUNT(*) FROM grantforge_user WHERE id = 9001 AND password = 'kept-hash'"));
        assertEquals(migrationCount, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
    }

    @Test
    public void legacyDatabaseUpgradesWithoutReimportingUsersOrPermissions() throws Exception
    {
        Path legacy = Paths.get(System.getProperty("grantforge.project.root"), "script/schema/schema.sql");
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(legacy));
        }
        execute("INSERT INTO authx_user (id, name, password, locked) VALUES (9001, 'legacy-user', 'legacy-hash', 1)");
        execute("INSERT INTO authx_user_role_relation (user_id, role_id) VALUES (9001, 2)");
        execute("ALTER TABLE authx_user DROP COLUMN email");
        migrate(flyway());
        assertEquals(1, number("SELECT COUNT(*) FROM grantforge_user WHERE id = 9001 AND password = 'legacy-hash' AND locked = 1"));
        assertEquals(1, number("SELECT COUNT(*) FROM grantforge_user_role_relation WHERE user_id = 9001 AND role_id = 2"));
        assertEquals(1, number("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'grantforge_user' AND column_name = 'email' AND character_maximum_length = 255"));
        migrate(flyway());
        assertEquals(4, number("SELECT COUNT(*) FROM grantforge_user"));
    }

    @Test
    public void laterSqlAddsAndChangesFieldsAndDataExactlyOnce() throws Exception
    {
        copyMigrations();
        migrate(flyway());
        execute("INSERT INTO grantforge_user (id, name, password) VALUES (9001, 'migration-user', 'kept-hash')");
        Files.write(nextMigrationFile("test_incremental_upgrade"), (
                "ALTER TABLE grantforge_user ADD COLUMN migration_probe varchar(32);\n"
                        + "ALTER TABLE grantforge_user MODIFY COLUMN email varchar(512);\n"
                        + "UPDATE grantforge_user SET name = 'upgraded-user' WHERE id = 9001;\n")
                .getBytes(StandardCharsets.UTF_8));
        migrate(flyway());
        migrate(flyway());
        assertEquals(1, number("SELECT COUNT(*) FROM grantforge_user WHERE id = 9001 AND name = 'upgraded-user' AND password = 'kept-hash'"));
        assertEquals(migrationCount + 1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(1, number("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'grantforge_user' AND column_name = 'email' AND character_maximum_length = 512"));
    }

    @Test
    public void modifiedPublishedSqlIsRejected() throws Exception
    {
        copyMigrations();
        migrate(flyway());
        Files.write(migrations.resolve("V3__complete_grantforge_schema.sql"), "SELECT 42;\n".getBytes(StandardCharsets.UTF_8));
        assertMigrationFails(flyway());
        assertEquals(migrationCount, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertTrue(tableExists("grantforge_user"));
    }

    @Test
    public void unrelatedNonEmptyDatabaseIsNotBaselined() throws Exception
    {
        execute("CREATE TABLE unrelated_data (id int PRIMARY KEY)");
        execute("INSERT INTO unrelated_data VALUES (1)");
        assertMigrationFails(flyway());
        assertEquals(1, number("SELECT COUNT(*) FROM unrelated_data"));
        assertFalse(tableExists("flyway_schema_history"));
    }

    @Test
    public void conflictingOldAndNewTablesAreNotRenamed() throws Exception
    {
        Path legacy = Paths.get(System.getProperty("grantforge.project.root"), "script/schema/schema.sql");
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(legacy));
        }
        execute("CREATE TABLE grantforge_user (id int PRIMARY KEY)");
        execute("INSERT INTO grantforge_user VALUES (9001)");
        assertMigrationFails(flyway());
        assertTrue(tableExists("authx_user"));
        assertEquals(1, number("SELECT COUNT(*) FROM grantforge_user WHERE id = 9001"));
        assertFalse(tableExists("flyway_schema_history"));
    }

    @Test
    public void invalidLegacyIdsAreRejectedBeforeAnyTableIsRenamed() throws Exception
    {
        Path legacy = Paths.get(System.getProperty("grantforge.project.root"), "script/schema/schema.sql");
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(legacy));
        }
        execute("INSERT INTO icon_type (id, name) VALUES (1, 'duplicate-id-test')");
        assertMigrationFails(flyway());
        assertTrue(tableExists("authx_user"));
        assertEquals(2, number("SELECT COUNT(*) FROM icon_type WHERE id = 1"));
        assertFalse(tableExists("flyway_schema_history"));
    }

    @Test
    public void widerLegacyTextFieldsAndTheirValuesArePreserved() throws Exception
    {
        Path legacy = Paths.get(System.getProperty("grantforge.project.root"), "script/schema/schema.sql");
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(legacy));
        }
        execute("ALTER TABLE authx_user MODIFY COLUMN email varchar(600)");
        execute("INSERT INTO authx_user (id, name, email) VALUES (9001, 'wide-field-user', REPEAT('e', 450))");
        execute("ALTER TABLE authx_menu MODIFY COLUMN description varchar(800)");
        execute("UPDATE authx_menu SET description = REPEAT('x', 700) WHERE id = 1");
        migrate(flyway());
        assertEquals(450, number("SELECT CHAR_LENGTH(email) FROM grantforge_user WHERE id = 9001"));
        assertEquals(700, number("SELECT CHAR_LENGTH(description) FROM grantforge_menu WHERE id = 1"));
    }

    @Test
    public void concurrentLegacyStartupAppliesEachVersionOnlyOnce() throws Exception
    {
        Path legacy = Paths.get(System.getProperty("grantforge.project.root"), "script/schema/schema.sql");
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(legacy));
        }
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = workers.submit(() -> {
                start.await();
                migrate(flyway());
                return null;
            });
            Future<?> second = workers.submit(() -> {
                start.await();
                migrate(flyway());
                return null;
            });
            start.countDown();
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        }
        finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(60, TimeUnit.SECONDS));
        }
        assertEquals(migrationCount, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(3, number("SELECT COUNT(*) FROM grantforge_user"));
    }

    @Test
    public void failedIncrementalSqlIsReportedAndNotRepairedAutomatically() throws Exception
    {
        copyMigrations();
        migrate(flyway());
        Files.write(nextMigrationFile("invalid_test_upgrade"),
                "ALTER TABLE missing_test_table ADD COLUMN broken int;\n".getBytes(StandardCharsets.UTF_8));
        assertMigrationFails(flyway());
        assertMigrationFails(flyway());
        assertTrue(tableExists("grantforge_user"));
        assertEquals(1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0"));
    }

    @Test
    public void cleanIsDisabled() throws Exception
    {
        migrate(flyway());
        try {
            flyway().clean();
            fail("Flyway clean must be disabled");
        }
        catch (FlywayException expected) {
            assertTrue(tableExists("grantforge_user"));
        }
    }

    @Test
    public void springBootMigratesBeforeJpaRepositoriesAreUsed() throws Exception
    {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                TestApplication.class, MySQLConfigure.class, DatabaseMigrationConfigure.class)
                .web(WebApplicationType.NONE)
                .properties("spring.datasource.url=" + databaseUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.flyway.clean-disabled=true",
                        "logging.level.root=ERROR")
                .run()) {
            assertEquals(3, context.getBean(UserRepository.class).count());
            assertTrue(tableExists("grantforge_user"));
        }
    }

    @Test
    public void invalidPendingSqlStopsSpringBootStartup() throws Exception
    {
        copyMigrations();
        migrate(flyway());
        Files.write(nextMigrationFile("invalid_startup_upgrade"),
                "ALTER TABLE missing_startup_table ADD COLUMN broken int;\n".getBytes(StandardCharsets.UTF_8));
        try {
            new SpringApplicationBuilder(TestApplication.class, MySQLConfigure.class, DatabaseMigrationConfigure.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.datasource.url=" + databaseUrl,
                            "spring.datasource.username=" + username,
                            "spring.datasource.password=" + password,
                            "spring.flyway.locations=filesystem:" + migrations,
                            "logging.level.root=OFF")
                    .run()
                    .close();
            fail("Invalid migration must stop application startup");
        }
        catch (RuntimeException expected) {
            assertEquals(1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0"));
            assertEquals(3, number("SELECT COUNT(*) FROM grantforge_user"));
        }
    }

    private Path nextMigrationFile(String description)
    {
        String version = flyway().info().current().getVersion().toString() + ".1";
        return migrations.resolve("V" + version + "__" + description + ".sql");
    }

    private void assertMigrationFails(Flyway flyway)
    {
        try {
            migrate(flyway);
            fail("Expected migration to be rejected");
        }
        catch (FlywayException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private void copyMigrations() throws IOException
    {
        migrations = Files.createTempDirectory("grantforge-migrations-");
        Path source = Paths.get("src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.copy(file, migrations.resolve(file.getFileName()));
            }
        }
    }

    private Flyway flyway()
    {
        String location = migrations == null ? "classpath:db/migration" : "filesystem:" + migrations;
        return Flyway.configure().dataSource(databaseUrl, username, password)
                .locations(location).cleanDisabled(true).validateMigrationNaming(true).load();
    }

    private void migrate(Flyway flyway)
    {
        new LegacyDatabaseMigrationStrategy().migrate(flyway);
    }

    private Connection connection() throws Exception
    {
        return DriverManager.getConnection(databaseUrl, username, password);
    }

    private boolean tableExists(String table) throws Exception
    {
        try (Connection connection = connection();
                ResultSet tables = connection.getMetaData().getTables(schema, null, table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private int number(String sql) throws Exception
    {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private void execute(String sql) throws Exception
    {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String requiredEnvironment(String name)
    {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Set " + name + " to run database integration tests");
        }
        return value;
    }

    @Configuration
    @EnableAutoConfiguration
    public static class TestApplication
    {
    }
}
