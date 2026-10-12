// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

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
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HiveProviderTest
{
    private final HiveProvider provider = new HiveProvider();

    private static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>();
        map.put(HiveProvider.URL, "jdbc:hive2://hs2:10000/default");
        map.put(HiveProvider.USER, "lookup");
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("warehouse", map);
    }

    private List<String> problems(ServiceConfig config)
    {
        return provider.validateConfig(config).stream().map(problem -> problem.field() + ":" + problem.reason()).toList();
    }

    @Test
    void definesHiveAsRangerDoes()
    {
        ServiceTypeDefinition type = provider.definition();
        assertThat(type.name()).isEqualTo("hive");
        assertThat(type.resources()).extracting(ResourceDefinition::name).containsExactly("database", "table", "udf", "column", "url",
                "hiveservice", "global");
        assertThat(type.resources().subList(0, 4)).allSatisfy(level -> {
            assertThat(level.matcher()).isEqualTo(MatcherType.WILDCARD);
            assertThat(level.caseSensitive()).as("Hive names have no case").isFalse();
            assertThat(level.lookupSupported()).isTrue();
        });
        ResourceDefinition url = type.resources().get(4);
        assertThat(url.matcher()).isEqualTo(MatcherType.PATH);
        assertThat(url.recursiveSupported()).isTrue();
        assertThat(url.accessTypes()).containsExactlyInAnyOrder("read", "write");
        assertThat(type.resources().get(5).accessTypes()).containsExactly("serviceadmin");
        assertThat(type.resources().get(6).accessTypes()).containsExactly("repladmin");
        assertThat(type.accessTypes()).extracting(access -> access.name()).containsExactly("select", "update", "create", "drop", "alter",
                "index", "lock", "read", "write", "repladmin", "serviceadmin", "tempudfadmin", "refresh", "all");
        assertThat(type.dataMask()).isNotNull();
        assertThat(type.dataMask().resources()).containsExactly("column");
        assertThat(type.dataMask().maskTypes()).extracting(MaskTypeDefinition::transformer).contains("mask({col})", "mask_hash({col})",
                "mask_show_last_n({col}, 4, 'x', 'x', 'x', -1, '1')", "{expr}");
        assertThat(type.rowFilter()).isNotNull();
        assertThat(type.rowFilter().resources()).containsExactly("table");
    }

    @Test
    void acceptsPasswordAndKerberosServices()
    {
        assertThat(problems(config())).isEmpty();
        assertThat(problems(config(HiveProvider.PASSWORD, "secret", HiveProvider.AUTHENTICATION, "password"))).isEmpty();
        assertThat(problems(config(HiveProvider.AUTHENTICATION, "kerberos", HiveProvider.USER, "lookup@EXAMPLE.COM", HiveProvider.KEYTAB,
                "/etc/lookup.keytab", HiveProvider.KDC, "kdc1, kdc2:88", HiveProvider.URL,
                "jdbc:hive2://hs2:10000/default;principal=hive/_HOST@EXAMPLE.COM"))).isEmpty();
    }

    @Test
    void namesEveryFieldThatDoesNotFit()
    {
        assertThat(problems(config(HiveProvider.AUTHENTICATION, "ldap"))).containsExactly("authentication:INVALID");
        assertThat(problems(config(HiveProvider.URL, "jdbc:hive2://hs2:10000/;user=x"))).containsExactly("jdbc.url:INVALID");
        assertThat(problems(config(HiveProvider.KEYTAB, "/k", HiveProvider.KDC, "kdc"))).containsExactly("keytab:INVALID", "kerberos.kdc:INVALID");
        assertThat(problems(config(HiveProvider.AUTHENTICATION, "kerberos", HiveProvider.KDC, "kdc:x", HiveProvider.URL,
                "jdbc:hive2://hs2:10000/;principal=hive/_HOST@R"))).containsExactly("username:INVALID", "password:REQUIRED",
                "kerberos.kdc:INVALID");
        assertThat(problems(config(HiveProvider.READ_TIMEOUT, "0"))).containsExactly("read.timeout.ms:INVALID");
        assertThat(provider.validateConfig(config(HiveProvider.URL, " "))).isEmpty();
    }

    @Test
    void namesWhyALookupFailed()
    {
        assertThat(HiveProvider.failure(new SQLException("Could not open client transport", "08S01", 0,
                new SaslException("Peer indicated failure: Error validating the login"))).getReason())
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(HiveProvider.failure(new LoginException("refused")).getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(HiveProvider.failure(new SQLException("Permission denied: user [bob] does not have [SELECT]")).getReason())
                .isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(HiveProvider.failure(new SQLException("no", "42000")).getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(HiveProvider.failure(new SQLException("Could not open client transport", "08S01", 0,
                new ConnectException("Connection refused"))).getReason()).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(HiveProvider.failure(new IOException("tls", new SSLHandshakeException("untrusted"))).getReason())
                .isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(HiveProvider.failure(new SQLException("Error while compiling")).getReason()).isEqualTo(LookupException.Reason.FAILED);
        assertThat(HiveProvider.message(new SQLException("first\nsecond"))).isEqualTo("first");
        assertThat(HiveProvider.message(new SQLException())).isEqualTo("SQLException");
    }

    @Test
    void looksUpOnlyLevelsWithNames()
    {
        for (String level : List.of("url", "hiveservice", "global")) {
            assertThat(provider.lookup(new LookupRequest(config(), level, "", Map.of(), 10))).as(level).isEmpty();
        }
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(HiveProvider.URL, "jdbc:mysql://x"), "database", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void failsToReachAPortNoServerListensOn() throws IOException
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ServiceConfig closed = config(HiveProvider.URL, "jdbc:hive2://127.0.0.1:" + port + "/default", HiveProvider.READ_TIMEOUT, "2000");
        ConnectionResult result = provider.testConnection(closed);
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).isNotBlank();
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(closed, "database", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(config(HiveProvider.URL, "jdbc:hive2://")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void reportsConfigProblemsWithTheirDetails()
    {
        List<ConfigProblem> problems = provider.validateConfig(config(HiveProvider.URL, "jdbc:hive2://hs2:10000/;auth=browser"));
        assertThat(problems).singleElement().satisfies(problem -> assertThat(problem.detail()).contains("noSasl"));
    }
}
