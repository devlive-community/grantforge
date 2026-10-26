// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

class PostgreSqlSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(PostgreSqlSettings.HOSTS, "pg1.example.com:5432", PostgreSqlSettings.USER, "lookup"));
        for (int index = 0; index < values.length; index += 2) {
            if (UNSET.equals(values[index + 1])) {
                map.remove(values[index]);
            }
            else {
                map.put(values[index], values[index + 1]);
            }
        }
        return new ServiceConfig("warehouse", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return PostgreSqlSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsAServerWithAPasswordAndTheDefaults()
    {
        ServiceConfig config = config(PostgreSqlSettings.PASSWORD, " secret ");
        assertThat(PostgreSqlSettings.problems(config)).isEmpty();
        PostgreSqlSettings settings = PostgreSqlSettings.of(config);
        assertThat(settings.hosts()).containsExactly("pg1.example.com:5432");
        assertThat(settings.database()).isEqualTo("postgres");
        assertThat(settings.authentication()).isEqualTo(PostgreSqlSettings.PASSWORD_SIGN_IN);
        assertThat(settings.user()).isEqualTo("lookup");
        assertThat(settings.password()).as("a password is taken as it is").isEqualTo(" secret ");
        assertThat(settings.sslMode()).isEqualTo("prefer");
        assertThat(settings.rootCertificate()).isNull();
        assertThat(settings.timeout()).isEqualTo(PostgreSqlSettings.DEFAULT_TIMEOUT);
    }

    @Test
    void readsSeveralServersOnceWithTheDefaultPort()
    {
        assertThat(PostgreSqlSettings.hosts("PG1, pg2:6432,pg1:5432, [::1]:5433, 10.0.0.7"))
                .containsExactly("pg1:5432", "pg2:6432", "[::1]:5433", "10.0.0.7:5432");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pg1:0", "pg1:70000", "pg1:", "jdbc:postgresql://pg1", "pg1/sales", "pg1,,pg2", "-pg1", "pg 1", "user@pg1", "::1"})
    void refusesWhatIsNoListOfServers(String value)
    {
        assertThat(PostgreSqlSettings.hosts(value)).isEmpty();
        assertThat(fields(config(PostgreSqlSettings.HOSTS, value))).containsExactly(PostgreSqlSettings.HOSTS);
    }

    @Test
    void needsTheServersAndTheUser()
    {
        assertThat(PostgreSqlSettings.problems(config(PostgreSqlSettings.HOSTS, UNSET, PostgreSqlSettings.USER, " ")))
                .extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(PostgreSqlSettings.HOSTS, ConfigProblem.Reason.REQUIRED), tuple(PostgreSqlSettings.USER, ConfigProblem.Reason.REQUIRED));
    }

    @Test
    void readsTheDatabaseToSignInTo()
    {
        assertThat(PostgreSqlSettings.of(config(PostgreSqlSettings.DATABASE, " sales ")).database()).isEqualTo("sales");
        assertThat(fields(config(PostgreSqlSettings.DATABASE, "d".repeat(64)))).containsExactly(PostgreSqlSettings.DATABASE);
    }

    @Test
    void readsACertificateSignIn()
    {
        ServiceConfig config = config(PostgreSqlSettings.AUTHENTICATION, "Certificate", PostgreSqlSettings.SSL_MODE, "VERIFY-FULL",
                PostgreSqlSettings.ROOT_CERTIFICATE, "/etc/ca.pem", PostgreSqlSettings.CLIENT_CERTIFICATE, "/etc/c.pem", PostgreSqlSettings.CLIENT_KEY,
                "/etc/c.key");
        assertThat(PostgreSqlSettings.problems(config)).isEmpty();
        PostgreSqlSettings settings = PostgreSqlSettings.of(config);
        assertThat(settings.authentication()).isEqualTo(PostgreSqlSettings.CERTIFICATE_SIGN_IN);
        assertThat(settings.sslMode()).isEqualTo("verify-full");
        assertThat(settings.clientCertificate()).isEqualTo("/etc/c.pem");
        assertThat(settings.clientKey()).isEqualTo("/etc/c.key");
        assertThat(PostgreSqlSettings.problems(config(PostgreSqlSettings.AUTHENTICATION, "certificate", PostgreSqlSettings.SSL_MODE, "require",
                PostgreSqlSettings.CLIENT_CERTIFICATE, "/etc/c.pem", PostgreSqlSettings.CLIENT_KEY, "/etc/c.key"))).as("TLS without checking the server")
                .isEmpty();
    }

    @Test
    void aCertificateSignInNeedsItsFilesAndTls()
    {
        assertThat(fields(config(PostgreSqlSettings.AUTHENTICATION, "certificate", PostgreSqlSettings.SSL_MODE, "require")))
                .containsExactly(PostgreSqlSettings.CLIENT_CERTIFICATE, PostgreSqlSettings.CLIENT_KEY);
        assertThat(fields(config(PostgreSqlSettings.AUTHENTICATION, "certificate", PostgreSqlSettings.CLIENT_CERTIFICATE, "/c.pem",
                PostgreSqlSettings.CLIENT_KEY, "/c.key"))).as("prefer may sign in without TLS").containsExactly(PostgreSqlSettings.SSL_MODE);
        assertThat(PostgreSqlSettings.problems(config(PostgreSqlSettings.AUTHENTICATION, "certificate", PostgreSqlSettings.SSL_MODE, "verify-ca",
                PostgreSqlSettings.CLIENT_CERTIFICATE, "/c.pem", PostgreSqlSettings.CLIENT_KEY, "/c.key")))
                .extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(PostgreSqlSettings.ROOT_CERTIFICATE, ConfigProblem.Reason.REQUIRED));
    }

    @Test
    void eachSignInRefusesTheFieldsOfTheOther()
    {
        assertThat(fields(config(PostgreSqlSettings.CLIENT_KEY, "/c.key"))).containsExactly(PostgreSqlSettings.CLIENT_KEY);
        assertThat(fields(config(PostgreSqlSettings.AUTHENTICATION, "certificate", PostgreSqlSettings.SSL_MODE, "require", PostgreSqlSettings.PASSWORD,
                "x", PostgreSqlSettings.CLIENT_CERTIFICATE, "/c.pem", PostgreSqlSettings.CLIENT_KEY, "/c.key"))).containsExactly(PostgreSqlSettings.PASSWORD);
        assertThat(fields(config(PostgreSqlSettings.AUTHENTICATION, "kerberos"))).containsExactly(PostgreSqlSettings.AUTHENTICATION);
    }

    @Test
    void checksTheTlsMode()
    {
        assertThat(fields(config(PostgreSqlSettings.SSL_MODE, "on"))).containsExactly(PostgreSqlSettings.SSL_MODE);
        assertThat(fields(config(PostgreSqlSettings.SSL_MODE, "require", PostgreSqlSettings.ROOT_CERTIFICATE, "/ca.pem")))
                .as("a CA certificate checks nothing without verify-ca or verify-full").containsExactly(PostgreSqlSettings.ROOT_CERTIFICATE);
        assertThat(PostgreSqlSettings.problems(config(PostgreSqlSettings.SSL_MODE, "verify-ca"))).as("the JVM's CAs verify the server").isEmpty();
        assertThat(PostgreSqlSettings.verifies("verify-ca")).isTrue();
        assertThat(PostgreSqlSettings.verifies("require")).isFalse();
    }

    @Test
    void readsTheTimeout()
    {
        assertThat(PostgreSqlSettings.of(config(PostgreSqlSettings.TIMEOUT, "1500")).timeout()).isEqualTo(1500);
        for (String wrong : List.of("0", "600001", "1.5", "-3", "soon")) {
            assertThat(fields(config(PostgreSqlSettings.TIMEOUT, wrong))).as(wrong).containsExactly(PostgreSqlSettings.TIMEOUT);
        }
    }

    @Test
    void unusableSettingsNameTheirFirstProblem()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> PostgreSqlSettings.of(config(PostgreSqlSettings.HOSTS, UNSET)))
                .withMessageStartingWith(PostgreSqlSettings.HOSTS + ": ");
    }
}
