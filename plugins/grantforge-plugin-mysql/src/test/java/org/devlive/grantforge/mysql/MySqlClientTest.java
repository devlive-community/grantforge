// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlClientTest
{
    @Test
    void translatesOnlyTheDriverNameAndPreservesFailoverHostsAndTlsSettings()
    {
        String suffix = "replication://address=(host=primary)(port=3306)(type=primary),replica:3306/sales"
                + "?sslMode=verify-full&serverSslCert=/etc/mysql/ca.pem";

        assertThat(MySqlClient.driverUrl("  jdbc:mysql:" + suffix + "  "))
                .isEqualTo("jdbc:mariadb:" + suffix);
        assertThat(MySqlClient.driverUrl("jdbc:mariadb://[::1]:3306/sales?sslMode=verify-full"))
                .isEqualTo("jdbc:mariadb://[::1]:3306/sales?sslMode=verify-full");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://db/sales", "jdbc:mysql:db", "jdbc:mysql://", "jdbc:mysql://db#fragment"})
    void rejectsUrlsThatCannotIdentifyAMySqlServer(String url)
    {
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.driverUrl(url))
                .withMessageContaining("use jdbc:mysql://host:3306");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "password", "allowLocalInfile", "allowMultiQueries", "initSql", "createDatabaseIfNotExist",
            "socketFactory", "credentialType", "pool", "poolName", "registerJmxPool"})
    void rejectsEveryReservedOptionInBothConfigurationFormsWithoutCaseBypasses(String key)
    {
        String upperCase = key.toUpperCase(Locale.ROOT);

        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.driverUrl("jdbc:mysql://db?" + upperCase + "=value"))
                .withMessageContaining("cannot be set for a service");
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.options(upperCase + "=value"))
                .withMessageContaining("cannot be set for a service");
    }

    @Test
    void rejectsEmbeddedCredentialsAndEncodedOptionNames()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.driverUrl("jdbc:mysql://lookup:secret@db/sales"))
                .withMessageContaining("own fields");
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.driverUrl("jdbc:mysql://db?%75ser=lookup"))
                .withMessageContaining("no driver option name");
    }

    @Test
    void parsesCommentedOptionsInOrderWithoutSplittingValuesAtLaterEqualsSigns()
    {
        Map<String, String> options = MySqlClient.options("  # TLS configuration\r\n\n sslMode = verify-full\r\n"
                + "serverSslCert = /etc/ca=production.pem\r\n connectionAttributes= ");

        assertThat(options).containsExactly(Map.entry("sslMode", "verify-full"),
                Map.entry("serverSslCert", "/etc/ca=production.pem"), Map.entry("connectionAttributes", ""));
        assertThat(MySqlClient.options(null)).isEmpty();
        assertThat(MySqlClient.options(" # nothing configured\n\n")).isEmpty();
    }

    @Test
    void rejectsMalformedAndDuplicateOptionNamesInsteadOfSilentlyOverwritingThem()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.options("# options\nsslMode=verify-full\nmissingValue"))
                .withMessageContaining("line 3");
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.options("=emptyName"))
                .withMessageContaining("line 1");
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.options("sslMode=verify-full\nSSLMODE=trust"))
                .withMessageContaining("given twice");
        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.options("ssl Mode=trust"))
                .withMessageContaining("key=value");
    }

    @Test
    void isolatesDriverPropertiesAndKeepsTheExactPassword()
    {
        Map<String, String> values = values();
        values.put(MySqlProvider.USER, "  lookup  ");
        values.put(MySqlProvider.PASSWORD, "  exact password  ");
        values.put(MySqlProvider.PROPERTIES, "sslMode=verify-full\nserverSslCert=/etc/ca.pem");
        values.put(MySqlProvider.CONNECT_TIMEOUT, "2500");
        values.put(MySqlProvider.READ_TIMEOUT, "4500");
        MySqlClient client = new MySqlClient(new ServiceConfig("sales", values));
        Properties properties = client.properties();

        assertThat(properties).containsEntry("user", "lookup").containsEntry("password", "  exact password  ")
                .containsEntry("sslMode", "verify-full").containsEntry("serverSslCert", "/etc/ca.pem")
                .containsEntry("allowLocalInfile", "false").containsEntry("allowMultiQueries", "false")
                .containsEntry("connectTimeout", "2500").containsEntry("socketTimeout", "4500");
        values.put(MySqlProvider.PASSWORD, "replacement");
        properties.clear();
        assertThat(client.properties()).containsEntry("password", "  exact password  ")
                .containsEntry("sslMode", "verify-full").containsEntry("allowLocalInfile", "false")
                .containsEntry("allowMultiQueries", "false").containsEntry("connectTimeout", "2500");
    }

    @Test
    void leavesAnAbsentPasswordUnsetAndAppliesBoundedTimeoutDefaults()
    {
        MySqlClient client = new MySqlClient(new ServiceConfig("sales", values()));

        assertThat(client.url()).isEqualTo("jdbc:mariadb://db:3306/sales");
        assertThat(client.properties()).doesNotContainKey("password")
                .containsEntry("connectTimeout", "10000").containsEntry("socketTimeout", "30000");
        Map<String, String> boundaries = values();
        boundaries.put(MySqlProvider.CONNECT_TIMEOUT, "1");
        boundaries.put(MySqlProvider.READ_TIMEOUT, "600000");
        assertThat(new MySqlClient(new ServiceConfig("sales", boundaries)).properties())
                .containsEntry("connectTimeout", "1").containsEntry("socketTimeout", "600000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "600001", "2147483648", "9223372036854775807"})
    void rejectsTimeoutsBeforeAnyNarrowingConversion(String value)
    {
        Map<String, String> values = values();
        values.put(MySqlProvider.CONNECT_TIMEOUT, value);
        ServiceConfig config = new ServiceConfig("sales", values);

        assertThatIllegalArgumentException().isThrownBy(() -> MySqlClient.timeout(config, MySqlProvider.CONNECT_TIMEOUT, 10_000))
                .withMessage("the timeout must be between 1 and 600000 milliseconds");
    }

    @ParameterizedTest
    @ValueSource(strings = {"one second", "1.5", "9223372036854775808"})
    void rejectsTimeoutsThatAreNotWholeMilliseconds(String value)
    {
        Map<String, String> values = values();
        values.put(MySqlProvider.READ_TIMEOUT, value);
        ServiceConfig config = new ServiceConfig("sales", values);

        assertThatThrownBy(() -> new MySqlClient(config)).isInstanceOf(NumberFormatException.class);
    }

    private static Map<String, String> values()
    {
        Map<String, String> values = new HashMap<>();
        values.put(MySqlProvider.URL, "jdbc:mysql://db:3306/sales");
        values.put(MySqlProvider.USER, "lookup");
        return values;
    }
}
