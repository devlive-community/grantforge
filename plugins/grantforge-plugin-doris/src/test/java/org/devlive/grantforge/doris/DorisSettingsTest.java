// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

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

class DorisSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(DorisSettings.HOSTS, "fe1.example.com:9030", DorisSettings.USER, "lookup"));
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
        return DorisSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsAFrontendWithAPasswordAndTheDefaults()
    {
        ServiceConfig config = config(DorisSettings.PASSWORD, " secret ");
        assertThat(DorisSettings.problems(config)).isEmpty();
        DorisSettings settings = DorisSettings.of(config);
        assertThat(settings.hosts()).containsExactly("fe1.example.com:9030");
        assertThat(settings.user()).isEqualTo("lookup");
        assertThat(settings.password()).as("a password is taken as it is").isEqualTo(" secret ");
        assertThat(settings.sslMode()).isEqualTo(DorisSettings.DISABLE);
        assertThat(settings.rootCertificate()).isNull();
        assertThat(settings.timeout()).isEqualTo(DorisSettings.DEFAULT_TIMEOUT);
        assertThat(settings.toString()).as("the password stays out of logs").doesNotContain("secret");
    }

    @Test
    void readsSeveralFrontendsOnceAddingTheDefaultPort()
    {
        assertThat(DorisSettings.hosts("FE1, fe2:19030,fe1:9030,[::1]:9030,10.0.0.7")).containsExactly("fe1:9030", "fe2:19030", "[::1]:9030",
                "10.0.0.7:9030");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fe1:0", "fe1:65536", "fe1:port", "fe1,,fe2", "jdbc:mysql://fe1:9030", "fe 1", "-fe1", "user@fe1:9030"})
    void refusesWhatIsNoListOfFrontends(String value)
    {
        assertThat(DorisSettings.hosts(value)).isEmpty();
        assertThat(fields(config(DorisSettings.HOSTS, value))).containsExactly(DorisSettings.HOSTS);
    }

    @Test
    void needsTheFrontendsAndTheUser()
    {
        assertThat(DorisSettings.problems(config(DorisSettings.HOSTS, UNSET, DorisSettings.USER, " ")))
                .extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(DorisSettings.HOSTS, ConfigProblem.Reason.REQUIRED), tuple(DorisSettings.USER, ConfigProblem.Reason.REQUIRED));
    }

    @Test
    void readsTheTlsModesInAnyCase()
    {
        DorisSettings settings = DorisSettings.of(config(DorisSettings.SSL_MODE, "Verify-Full", DorisSettings.ROOT_CERTIFICATE, " /etc/doris/ca.pem "));
        assertThat(settings.sslMode()).isEqualTo(DorisSettings.VERIFY_FULL);
        assertThat(settings.rootCertificate()).isEqualTo("/etc/doris/ca.pem");
        assertThat(DorisSettings.of(config(DorisSettings.SSL_MODE, "trust")).sslMode()).isEqualTo(DorisSettings.TRUST);
    }

    @Test
    void refusesAnUnknownTlsModeAndACertificateNothingVerifies()
    {
        assertThat(fields(config(DorisSettings.SSL_MODE, "required"))).containsExactly(DorisSettings.SSL_MODE);
        assertThat(fields(config(DorisSettings.SSL_MODE, "trust", DorisSettings.ROOT_CERTIFICATE, "/ca.pem")))
                .containsExactly(DorisSettings.ROOT_CERTIFICATE);
        assertThat(fields(config(DorisSettings.ROOT_CERTIFICATE, "/ca.pem"))).as("the default mode verifies nothing")
                .containsExactly(DorisSettings.ROOT_CERTIFICATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-5", "1.5", "soon"})
    void refusesATimeoutOutOfRange(String value)
    {
        assertThat(fields(config(DorisSettings.TIMEOUT, value))).containsExactly(DorisSettings.TIMEOUT);
    }

    @Test
    void readsATimeout()
    {
        assertThat(DorisSettings.of(config(DorisSettings.TIMEOUT, " 600000 ")).timeout()).isEqualTo(600_000);
        assertThat(DorisSettings.of(config(DorisSettings.TIMEOUT, "1")).timeout()).isEqualTo(1);
    }

    @Test
    void namesTheFirstProblemWhenReadingUnusableSettings()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> DorisSettings.of(config(DorisSettings.HOSTS, UNSET)))
                .withMessageStartingWith(DorisSettings.HOSTS + ": ");
    }
}
