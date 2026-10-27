// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DorisProviderTest
{
    /** What the fake cluster answers, by the start of a statement: the column names, then the rows. */
    private final Map<String, List<List<String>>> answers = new LinkedHashMap<>();
    private final List<String> asked = new ArrayList<>();
    private @Nullable SQLException failure;

    @SafeVarargs
    private DorisProviderTest answer(String start, List<String>... rows)
    {
        answers.put(start, List.of(rows));
        return this;
    }

    private DorisProvider provider()
    {
        return new DorisProvider(settings -> new DorisClient(settings)
        {
            @Override
            Connection connect() throws SQLException
            {
                SQLException failing = failure;
                if (failing != null) {
                    throw failing;
                }
                Connection connection = mock(Connection.class);
                Statement statement = mock(Statement.class);
                when(connection.createStatement()).thenReturn(statement);
                when(statement.executeQuery(anyString())).thenAnswer(call -> rows(call.getArgument(0)));
                return connection;
            }
        });
    }

    /** The rows of the first answer whose start the statement has; the first row names the columns. */
    private ResultSet rows(String sql) throws SQLException
    {
        asked.add(sql);
        List<List<String>> table = answers.entrySet().stream().filter(answer -> sql.startsWith(answer.getKey())).map(Map.Entry::getValue).findFirst()
                .orElse(List.of(List.of("Name")));
        List<String> columns = table.get(0);
        List<List<String>> rows = table.subList(1, table.size());
        ResultSet result = mock(ResultSet.class);
        int[] at = {-1};
        when(result.next()).thenAnswer(call -> ++at[0] < rows.size());
        when(result.getString(anyInt())).thenAnswer(call -> rows.get(at[0]).get((int) call.getArgument(0) - 1));
        when(result.findColumn(anyString())).thenAnswer(call -> columns.indexOf((String) call.getArgument(0)) + 1);
        return result;
    }

    private static ServiceConfig config()
    {
        return new ServiceConfig("warehouse", Map.of(DorisSettings.HOSTS, "fe1:9030", DorisSettings.USER, "lookup"));
    }

    private static LookupRequest request(String resource, String typed, Map<String, List<String>> context)
    {
        return new LookupRequest(config(), resource, typed, context, 10);
    }

    @Test
    void describesDorisInItsOwnTerms()
    {
        ServiceTypeDefinition definition = provider().definition();
        assertThat(definition.name()).isEqualTo("doris");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("catalog", "database", "table", "column",
                "workload_group");
        assertThat(definition.resources()).filteredOn(resource -> "column".equals(resource.name())).singleElement()
                .satisfies(column -> assertThat(column.caseSensitive()).as("Doris compares column names without case").isFalse());
        assertThat(definition.accessTypes()).hasSize(9);
        assertThat(definition.rowFilter()).isNotNull();
        assertThat(definition.dataMask()).as("Doris masks no values").isNull();
        assertThat(provider().validateConfig(config())).isEmpty();
        assertThat(new DorisProvider().definition().name()).isEqualTo("doris");
    }

    @Test
    void describesTheClusterOnATestWithShowStatementsOnly()
    {
        answer("SHOW VARIABLES", List.of("Variable_name", "Value"), List.of("version_comment", "doris version doris-4.1.5-release-917b20be838"))
                .answer("SHOW GRANTS", List.of("UserIdentity"), List.of("'lookup'@'%'"))
                .answer("SHOW CATALOGS", List.of("CatalogId", "CatalogName"), List.of("0", "internal"), List.of("1", "hive"))
                .answer("SHOW DATABASES", List.of("Database"), List.of("sales"), List.of("information_schema"), List.of("mysql"));
        ConnectionResult result = provider().testConnection(config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("Apache Doris 4.1.5, signed in as 'lookup'@'%', 2 catalogs, 1 database in the internal catalog");
        assertThat(asked).noneMatch(sql -> sql.startsWith("SELECT"));

        answers.clear();
        answer("SHOW DATABASES", List.of("Database"));
        assertThat(provider().testConnection(config()).message()).isEqualTo("Apache Doris of an unknown release, signed in as an unknown user, 1 catalog,"
                + " 0 databases in the internal catalog");
    }

    @Test
    void saysWhyATestFailed()
    {
        failure = new SQLException("Access denied for user 'lookup'", "28000", 1045);
        assertThat(provider().testConnection(config()).message()).startsWith("Doris refused the sign-in");
        assertThat(provider().testConnection(new ServiceConfig("warehouse", Map.of(DorisSettings.USER, "lookup"))).status())
                .isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void looksUpCatalogsDatabasesTablesColumnsAndWorkloadGroups()
    {
        answer("SHOW CATALOGS", List.of("CatalogId", "CatalogName"), List.of("1", "hive"), List.of("0", "internal"))
                .answer("SHOW DATABASES FROM `internal`", List.of("Database"), List.of("sales"), List.of("sandbox"), List.of("__internal_schema"))
                .answer("SHOW DATABASES FROM `hive`", List.of("Database"), List.of("lake"))
                .answer("SHOW TABLES FROM `internal`.`sales`", List.of("Tables_in_sales"), List.of("orders"), List.of("items"))
                .answer("SHOW TABLES FROM `internal`.`sandbox`", List.of("Tables_in_sandbox"), List.of("scratch"))
                .answer("SHOW COLUMNS FROM `internal`.`sales`.`orders`", List.of("Field", "Type"), List.of("id", "int"), List.of("Region", "varchar"))
                .answer("SHOW WORKLOAD GROUPS", List.of("Id", "Name", "Item"), List.of("1", "normal", "cpu"), List.of("1", "normal", "memory"),
                        List.of("2", "etl", "cpu"));
        DorisProvider provider = provider();
        assertThat(provider.lookup(request("catalog", "", Map.of()))).containsExactly("hive", "internal");
        assertThat(provider.lookup(request("database", "s", Map.of("catalog", List.of("internal"))))).containsExactly("sales", "sandbox");
        assertThat(provider.lookup(request("database", "", Map.of()))).as("every catalog").containsExactly("lake", "sales", "sandbox");
        assertThat(provider.lookup(request("table", "", Map.of("catalog", List.of("internal"), "database", List.of("sal*")))))
                .containsExactly("items", "orders");
        assertThat(provider.lookup(request("column", "reg", Map.of("catalog", List.of("internal"), "database", List.of("sales"), "table",
                List.of("ord*"))))).as("columns compare without case").containsExactly("Region");
        assertThat(provider.lookup(request("workload_group", "", Map.of()))).containsExactly("etl", "normal");
    }

    @Test
    void reportsWhyALookupFailed()
    {
        assertThatThrownBy(() -> provider().lookup(request("partition", "", Map.of()))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        failure = new SQLNonTransientConnectionException("refused");
        assertThatThrownBy(() -> provider().lookup(request("catalog", "", Map.of()))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }

    @Test
    void looksInAtMostAHundredTablesForColumns()
    {
        List<String>[] tables = new List[151];
        tables[0] = List.of("Tables_in_sales");
        for (int index = 1; index < tables.length; index++) {
            tables[index] = List.of("t" + index);
        }
        answer("SHOW CATALOGS", List.of("CatalogId", "CatalogName"), List.of("0", "internal"))
                .answer("SHOW DATABASES", List.of("Database"), List.of("sales")).answer("SHOW TABLES", tables)
                .answer("SHOW COLUMNS", List.of("Field"), List.of("c"));
        assertThat(provider().lookup(request("column", "", Map.of()))).containsExactly("c");
        assertThat(asked.stream().filter(sql -> sql.startsWith("SHOW COLUMNS")).count()).isEqualTo(DorisLookup.MAX_PARENTS);
    }
}
