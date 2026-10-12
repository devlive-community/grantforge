// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlProviderTest
{
    private final MySqlProvider provider = new MySqlProvider();

    @Test
    void definesDatabasesTablesAndColumnsWithMasksAndRowFilters()
    {
        ServiceTypeDefinition type = provider.definition();
        assertThat(type.name()).isEqualTo("mysql");
        assertThat(type.resources()).extracting(ResourceDefinition::name).containsExactly("database", "table", "column");
        assertThat(type.resources()).allSatisfy(level -> {
            assertThat(level.matcher()).isEqualTo(MatcherType.WILDCARD);
            assertThat(level.lookupSupported()).isTrue();
            assertThat(level.browseSupported()).isFalse();
        });
        ResourceDefinition table = type.resources().get(1);
        ResourceDefinition column = type.resources().get(2);
        assertThat(table.parent()).isEqualTo("database");
        assertThat(table.caseSensitive()).isTrue();
        assertThat(column.parent()).isEqualTo("table");
        assertThat(column.caseSensitive()).as("MySQL compares column names without case").isFalse();
        assertThat(column.accessTypes()).containsExactlyInAnyOrder("select", "insert", "update");
        assertThat(type.accessTypes()).extracting(access -> access.name())
                .containsExactly("select", "insert", "update", "delete", "create", "drop", "alter", "index", "all");
        assertThat(type.dataMask()).isNotNull();
        assertThat(type.dataMask().resources()).containsExactly("column");
        assertThat(type.dataMask().maskTypes()).extracting(MaskTypeDefinition::name).containsExactly("mask", "mask_show_last_4",
                "mask_show_first_4", "mask_hash", "mask_null", "mask_none", "mask_date_show_year", "custom");
        assertThat(type.dataMask().maskTypes()).filteredOn(mask -> !"mask_null".equals(mask.name()) && !"custom".equals(mask.name()))
                .allSatisfy(mask -> assertThat(mask.transformer()).contains("{col}"));
        assertThat(type.rowFilter()).isNotNull();
        assertThat(type.rowFilter().resources()).containsExactly("table");
    }

    @Test
    void acceptsBothDriversUrlsAndFailoverForms()
    {
        assertThat(provider.validateConfig(config("jdbc:mysql://db.example.com:3306"))).isEmpty();
        assertThat(provider.validateConfig(config("jdbc:mariadb://db1:3306,db2:3306/sales?sslMode=verify-full"))).isEmpty();
        assertThat(provider.validateConfig(config("jdbc:mysql:replication://primary,replica/"))).isEmpty();
        assertThat(provider.validateConfig(config("jdbc:mysql://address=(host=db)(port=3306)(type=primary)"))).isEmpty();
        assertThat(MySqlClient.driverUrl(" jdbc:mysql://db:3306/sales ")).isEqualTo("jdbc:mariadb://db:3306/sales");
        assertThat(MySqlClient.driverUrl("jdbc:mysql:loadbalance://a,b")).isEqualTo("jdbc:mariadb:loadbalance://a,b");
        assertThat(MySqlClient.driverUrl("jdbc:mariadb://db")).isEqualTo("jdbc:mariadb://db");
    }

    @Test
    void refusesUrlsThatAreNoMySqlServerOrCarryCredentials()
    {
        assertThat(problem(config("jdbc:postgresql://db/sales"), MySqlProvider.URL)).contains("jdbc:mysql://");
        assertThat(problem(config("jdbc:mysql:db"), MySqlProvider.URL)).contains("jdbc:mysql://");
        assertThat(problem(config("jdbc:mysql://"), MySqlProvider.URL)).contains("jdbc:mysql://");
        assertThat(problem(config("jdbc:mysql://root@db"), MySqlProvider.URL)).contains("own fields");
    }

    @Test
    void refusesReservedDriverOptionsInTheUrlAndTheOptionsWhateverTheirCase()
    {
        assertThat(problem(config("jdbc:mysql://db?allowLocalInfile=true"), MySqlProvider.URL)).contains("allowLocalInfile");
        assertThat(problem(config("jdbc:mysql://db?sslMode=trust&ALLOWLOCALINFILE=true"), MySqlProvider.URL)).contains("ALLOWLOCALINFILE");
        assertThat(problem(config("jdbc:mysql://db?password=x"), MySqlProvider.URL)).contains("password");
        assertThat(problem(config("jdbc:mysql://db?%75ser=x"), MySqlProvider.URL)).contains("no driver option name");
        assertThat(problem(config("jdbc:mysql://db", "initSql=DROP TABLE t"), MySqlProvider.PROPERTIES)).contains("initSql");
        assertThat(problem(config("jdbc:mysql://db", "socketFactory=com.example.Evil"), MySqlProvider.PROPERTIES)).contains("socketFactory");
        assertThat(problem(config("jdbc:mysql://db", "User=admin"), MySqlProvider.PROPERTIES)).contains("User");
        assertThat(problem(config("jdbc:mysql://db", "createDatabaseIfNotExist=true"), MySqlProvider.PROPERTIES)).isNotNull();
    }

    @Test
    void readsDriverOptionsLineByLine()
    {
        assertThat(MySqlClient.options("# TLS\nsslMode = verify-full\r\n\nserverSslCert=/etc/ca.pem\n"))
                .containsExactly(Map.entry("sslMode", "verify-full"), Map.entry("serverSslCert", "/etc/ca.pem"));
        assertThat(MySqlClient.options(null)).isEmpty();
        assertThat(problem(config("jdbc:mysql://db", "sslMode"), MySqlProvider.PROPERTIES)).contains("line 1");
        assertThat(problem(config("jdbc:mysql://db", "\n=x"), MySqlProvider.PROPERTIES)).contains("line 2");
        assertThat(problem(config("jdbc:mysql://db", "sslMode=trust\nSSLMODE=disable"), MySqlProvider.PROPERTIES)).contains("twice");
    }

    @Test
    void keepsTimeoutsWithinTenMinutes()
    {
        Map<String, String> values = new HashMap<>(config("jdbc:mysql://db").values());
        values.put(MySqlProvider.CONNECT_TIMEOUT, "0");
        values.put(MySqlProvider.READ_TIMEOUT, "600001");
        assertThat(provider.validateConfig(new ServiceConfig("sales", values))).extracting(ConfigProblem::field)
                .containsExactly(MySqlProvider.CONNECT_TIMEOUT, MySqlProvider.READ_TIMEOUT);
    }

    @Test
    void givesTheDriverTheServicesCredentialsAndNeverLocalFiles()
    {
        Map<String, String> values = new HashMap<>(config("jdbc:mysql://db:3306", "sslMode=trust").values());
        values.put(MySqlProvider.CONNECT_TIMEOUT, "2500");
        MySqlClient client = new MySqlClient(new ServiceConfig("sales", values));
        assertThat(client.url()).isEqualTo("jdbc:mariadb://db:3306");
        assertThat(client.properties()).containsEntry("user", "lookup").containsEntry("password", "secret")
                .containsEntry("sslMode", "trust").containsEntry("allowLocalInfile", "false").containsEntry("allowMultiQueries", "false")
                .containsEntry("connectTimeout", "2500").containsEntry("socketTimeout", "30000");
        values.remove(MySqlProvider.PASSWORD);
        assertThat(new MySqlClient(new ServiceConfig("sales", values)).properties()).doesNotContainKey("password");
    }

    @Test
    void acceptsMySqlEightAndLaterOnly()
    {
        assertThat(MySqlProvider.unsupported("8.0.40")).isNull();
        assertThat(MySqlProvider.unsupported("8.4.3")).isNull();
        assertThat(MySqlProvider.unsupported("9.1.0-commercial")).isNull();
        assertThat(MySqlProvider.unsupported("5.7.44-log")).contains("MySQL 8.0 or later");
        assertThat(MySqlProvider.unsupported("11.4.2-MariaDB-ubu2404")).contains("MariaDB");
        assertThat(MySqlProvider.unsupported("unknown")).contains("cannot be read");
    }

    @Test
    void namesWhyALookupFailed()
    {
        assertThat(reason(new SQLInvalidAuthorizationSpecException("Access denied for user", "28000", 1045)))
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new SQLException("Access denied", "HY000", 1045))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new SQLSyntaxErrorException("Access denied for user to database 'hr'", "42000", 1044)))
                .isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new SQLSyntaxErrorException("SELECT command denied", "42000", 1142)))
                .isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new SQLNonTransientConnectionException("Socket fail to connect", "08000", -1, new ConnectException("refused"))))
                .isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLTimeoutException("timed out"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("handshake", "HY000", 0, new SSLHandshakeException("untrusted"))))
                .isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("Unknown column", "42S22", 1054))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(MySqlProvider.failure(new SQLException("first line\nsecond line")).getMessage()).isEqualTo("first line");
    }

    @Test
    void failsToConnectToAPortNoServerListensOn() throws IOException
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Map<String, String> values = new HashMap<>(config("jdbc:mysql://127.0.0.1:" + port).values());
        values.put(MySqlProvider.CONNECT_TIMEOUT, "2000");
        ServiceConfig config = new ServiceConfig("sales", values);
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).isNotBlank();
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config, "database", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }

    @Test
    void refusesLookupsOfOtherLevelsAndBrokenServices()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config("jdbc:mysql://db"), "path", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config("jdbc:mysql://db?initSql=x"), "database", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThat(provider.testConnection(config("jdbc:oracle:thin:@db")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    private static LookupException.Reason reason(SQLException failure)
    {
        return MySqlProvider.failure(failure).getReason();
    }

    private String problem(ServiceConfig config, String field)
    {
        List<ConfigProblem> problems = provider.validateConfig(config);
        assertThat(problems).extracting(ConfigProblem::field).containsExactly(field);
        assertThat(problems.get(0).reason()).isEqualTo(ConfigProblem.Reason.INVALID);
        return String.valueOf(problems.get(0).detail());
    }

    private static ServiceConfig config(String url)
    {
        return new ServiceConfig("sales", Map.of(MySqlProvider.URL, url, MySqlProvider.USER, "lookup", MySqlProvider.PASSWORD, "secret"));
    }

    private static ServiceConfig config(String url, String options)
    {
        return new ServiceConfig("sales", Map.of(MySqlProvider.URL, url, MySqlProvider.USER, "lookup", MySqlProvider.PASSWORD, "secret",
                MySqlProvider.PROPERTIES, options));
    }
}
