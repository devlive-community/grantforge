// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import org.devlive.grantforge.mysql.agent.sql.TableName;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.Driver;
import org.mockito.MockedConstruction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * The catalog against a JDBC layer that stands in for MySQL: the driver is built where the catalog builds it, and the
 * answers it gives are the ones the tests write down.
 */
class JdbcCatalogTest
{
    private static final TableName ORDERS = new TableName("sales", "orders");

    private static final String HANDSHAKE = "SELECT @@lower_case_table_names, (SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES"
            + " WHERE PRIVILEGE_TYPE = 'SHOW_ROUTINE' AND GRANTEE = CONCAT('''', SUBSTRING_INDEX(CURRENT_USER(), '@', 1), '''@''',"
            + " SUBSTRING_INDEX(CURRENT_USER(), '@', -1), ''''))";

    private static final String COLUMNS = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE LOWER(TABLE_SCHEMA) = ? AND "
            + "LOWER(TABLE_NAME) = ? ORDER BY ORDINAL_POSITION";

    private static final String ROUTINES = "SELECT ROUTINE_NAME FROM information_schema.ROUTINES WHERE LOWER(ROUTINE_SCHEMA) = ?";

    @Test
    void readsTheColumnsAndTheRoutinesOfTheServerAndKeepsThem() throws Exception
    {
        try (Server server = new Server().columns(ORDERS, "id", "Card").routines("sales", "Discount")) {
            try (JdbcCatalog catalog = server.catalog(Duration.ofMinutes(5))) {
                assertThat(catalog.foldsNames()).isTrue();
                assertThat(catalog.columns(ORDERS)).containsExactly("id", "Card");
                assertThat(catalog.routine("sales", "DISCOUNT")).isTrue();
                assertThat(catalog.routine("sales", "missing")).isFalse();
                // A table the server does not have is one whose columns it does not answer.
                assertThat(catalog.columns(new TableName("sales", "missing"))).isNull();
                assertThat(catalog.columns(ORDERS)).containsExactly("id", "Card");
                assertThat(catalog.routine("sales", "discount")).isTrue();
            }
            assertThat(server.queries()).containsExactly(HANDSHAKE, COLUMNS, ROUTINES, COLUMNS);
            assertThat(server.opened()).isEqualTo(1);
        }
    }

    @Test
    void keepsNothingWhenTheCacheHasExpired() throws Exception
    {
        try (Server server = new Server().columns(ORDERS, "id")) {
            try (JdbcCatalog catalog = server.catalog(Duration.ZERO)) {
                assertThat(catalog.columns(ORDERS)).containsExactly("id");
                assertThat(catalog.columns(ORDERS)).containsExactly("id");
            }
            assertThat(server.queries().stream().filter(sql -> sql.startsWith("SELECT COLUMN_NAME"))).hasSize(2);
        }
    }

    @Test
    void forgetsWhatItKnowsWhenAStatementChangedTheSchema() throws Exception
    {
        try (Server server = new Server().columns(ORDERS, "id")) {
            try (JdbcCatalog catalog = server.catalog(Duration.ofMinutes(5))) {
                catalog.columns(ORDERS);
                catalog.changed();
                assertThat(catalog.columns(ORDERS)).containsExactly("id");
                catalog.routine("sales", "discount");
                catalog.changed();
                assertThat(catalog.routine("sales", "discount")).isFalse();
            }
            assertThat(server.queries().stream().filter(sql -> sql.startsWith("SELECT COLUMN_NAME"))).hasSize(2);
            assertThat(server.queries().stream().filter(sql -> sql.startsWith("SELECT ROUTINE_NAME"))).hasSize(2);
        }
    }

    @Test
    void readsTheSchemaAsTheServerFoldsIt() throws Exception
    {
        try (Server server = new Server().folds(0).columns(ORDERS, "id")) {
            try (JdbcCatalog catalog = server.catalog(Duration.ofMinutes(5))) {
                assertThat(catalog.foldsNames()).isFalse();
                assertThat(catalog.columns(ORDERS)).containsExactly("id");
            }
            assertThat(server.parameters()).containsExactly(List.of("sales", "orders"));
            assertThat(server.queries()).contains("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND "
                    + "TABLE_NAME = ? ORDER BY ORDINAL_POSITION");
        }
    }

    @Test
    void opensTheConnectionAgainWhenTheServerDroppedIt() throws Exception
    {
        try (Server server = new Server().columns(ORDERS, "id", "Card").failFirstRead()) {
            try (JdbcCatalog catalog = server.catalog(Duration.ZERO)) {
                assertThat(catalog.columns(ORDERS)).containsExactly("id", "Card");
            }
            assertThat(server.opened()).isEqualTo(2);
        }
    }

    @Test
    void givesUpWhenTheSchemaCannotBeReadASecondTime() throws Exception
    {
        try (Server server = new Server().columns(ORDERS, "id").failEveryRead()) {
            try (JdbcCatalog catalog = server.catalog(Duration.ZERO)) {
                assertThatIllegalStateException().isThrownBy(() -> catalog.columns(ORDERS))
                        .withMessageContaining("the MySQL schema cannot be read");
            }
            assertThat(server.opened()).isEqualTo(2);
        }
    }

    @Test
    void refusesToStartWithoutRoutineVisibility()
    {
        try (Server server = new Server().routinesVisible(0)) {
            assertThatThrownBy(() -> server.catalog(Duration.ofSeconds(30)))
                    .isInstanceOf(SQLException.class).hasMessageContaining("SHOW_ROUTINE");
            assertThat(server.closed()).isEqualTo(1);
        }
    }

    @Test
    void refusesToStartWhenTheServerDoesNotSayHowItFoldsNames()
    {
        try (Server server = new Server().answersNothing()) {
            assertThatThrownBy(() -> server.catalog(Duration.ofSeconds(30)))
                    .isInstanceOf(SQLException.class).hasMessageContaining("did not say how it folds names");
            assertThat(server.closed()).isEqualTo(1);
        }
    }

    @Test
    void letsGoOfTheConnectionWhenItCloses() throws Exception
    {
        try (Server server = new Server().columns(ORDERS, "id")) {
            JdbcCatalog catalog = server.catalog(Duration.ofMinutes(5));
            catalog.close();
            catalog.close();
            assertThat(server.closed()).isEqualTo(1);
        }
    }

    /** The server's answers, and what the catalog asked for. */
    private static final class Server
            implements AutoCloseable
    {
        private final Map<String, List<String>> columns = new LinkedHashMap<>();
        private final Map<String, List<String>> routines = new LinkedHashMap<>();
        private final List<String> queries = new ArrayList<>();
        private final List<List<String>> parameters = new ArrayList<>();
        private final List<SQLException> failures = new ArrayList<>();
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final MockedConstruction<Driver> drivers;
        private int folds = 1;
        private int visibleRoutines = 1;
        private boolean answersNothing;

        Server()
        {
            drivers = mockConstruction(Driver.class, (driver, context) -> when(driver.connect(anyString(), any(Properties.class)))
                    .thenAnswer(ignored -> {
                        opened.incrementAndGet();
                        return connection();
                    }));
        }

        Server columns(TableName table, String... names)
        {
            columns.put(table.toString(), Arrays.asList(names));
            return this;
        }

        Server routines(String database, String... names)
        {
            routines.put(database, Arrays.asList(names));
            return this;
        }

        Server folds(int value)
        {
            folds = value;
            return this;
        }

        Server routinesVisible(int count)
        {
            visibleRoutines = count;
            return this;
        }

        Server answersNothing()
        {
            answersNothing = true;
            return this;
        }

        Server failFirstRead()
        {
            failures.add(new SQLException("the server closed the connection", "08S01", 1927));
            return this;
        }

        Server failEveryRead()
        {
            for (int attempt = 0; attempt < 4; attempt++) {
                failFirstRead();
            }
            return this;
        }

        JdbcCatalog catalog(Duration keep) throws SQLException
        {
            return new JdbcCatalog("jdbc:mariadb://mysql:3306/", new Properties(), keep, clock());
        }

        List<String> queries()
        {
            return queries;
        }

        List<List<String>> parameters()
        {
            return parameters;
        }

        int opened()
        {
            return opened.get();
        }

        int closed()
        {
            return closed.get();
        }

        @Override
        public void close()
        {
            drivers.close();
        }

        private Connection connection() throws SQLException
        {
            // The driver hands out its own connection type, which extends the standard one.
            org.mariadb.jdbc.Connection open = mock(org.mariadb.jdbc.Connection.class);
            when(open.isClosed()).thenReturn(false);
            when(open.isReadOnly()).thenReturn(false);
            doAnswer(ignored -> {
                closed.incrementAndGet();
                return null;
            }).when(open).close();
            when(open.createStatement()).thenAnswer(ignored -> handshake());
            when(open.prepareStatement(anyString())).thenAnswer(call -> schema(call.getArgument(0)));
            return open;
        }

        private org.mariadb.jdbc.Statement handshake() throws SQLException
        {
            queries.add(HANDSHAKE);
            org.mariadb.jdbc.Statement statement = mock(org.mariadb.jdbc.Statement.class);
            when(statement.executeQuery(HANDSHAKE)).thenAnswer(ignored -> rows(answersNothing
                    ? List.of()
                    : List.of(Map.of(1, folds, 2, visibleRoutines))));
            return statement;
        }

        /** Answers a schema query, taking its parameters into account and failing as the server would. */
        private PreparedStatement schema(String sql) throws SQLException
        {
            queries.add(sql);
            List<String> given = new ArrayList<>();
            PreparedStatement statement = mock(PreparedStatement.class);
            doAnswer(call -> {
                given.add(call.getArgument(1));
                return null;
            }).when(statement).setString(anyInt(), anyString());
            when(statement.executeQuery()).thenAnswer(ignored -> {
                parameters.add(List.copyOf(given));
                if (!failures.isEmpty()) {
                    throw failures.remove(0);
                }
                return rows(rowsOf(sql, given));
            });
            return statement;
        }

        private List<Map<Integer, Object>> rowsOf(String sql, List<String> given)
        {
            List<Map<Integer, Object>> rows = new ArrayList<>();
            List<String> names = sql.startsWith("SELECT COLUMN_NAME")
                    ? columns.getOrDefault(given.get(0) + "." + given.get(1), List.of())
                    : routines.getOrDefault(given.get(0), List.of());
            for (String name : names) {
                rows.add(Map.of(1, name));
            }
            return rows;
        }

        private ResultSet rows(List<Map<Integer, Object>> values) throws SQLException
        {
            ResultSet rows = mock(ResultSet.class);
            AtomicInteger position = new AtomicInteger(-1);
            when(rows.next()).thenAnswer(ignored -> position.incrementAndGet() < values.size());
            when(rows.getInt(anyInt())).thenAnswer(call -> (Integer) values.get(position.get()).get(call.<Integer>getArgument(0)));
            when(rows.getString(anyInt())).thenAnswer(call -> (String) values.get(position.get()).get(call.<Integer>getArgument(0)));
            return rows;
        }
    }

    private static Clock clock()
    {
        return Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    }
}
