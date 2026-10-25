// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

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

class ClickHouseSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(ClickHouseSettings.URLS, "http://ch1.example.com:8123", ClickHouseSettings.USER, "lookup"));
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
        return ClickHouseSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsAServerWithAPasswordAndTheDefaults()
    {
        ServiceConfig config = config(ClickHouseSettings.PASSWORD, " secret ");
        assertThat(ClickHouseSettings.problems(config)).isEmpty();
        ClickHouseSettings settings = ClickHouseSettings.of(config);
        assertThat(settings.urls()).containsExactly("http://ch1.example.com:8123");
        assertThat(settings.authentication()).isEqualTo(ClickHouseSettings.PASSWORD_SIGN_IN);
        assertThat(settings.user()).isEqualTo("lookup");
        assertThat(settings.password()).as("a password is taken as it is").isEqualTo(" secret ");
        assertThat(settings.rootCertificate()).isNull();
        assertThat(settings.timeout()).isEqualTo(ClickHouseSettings.DEFAULT_TIMEOUT);
    }

    @Test
    void readsSeveralServersOnceWithLowerCaseSchemes()
    {
        assertThat(ClickHouseSettings.urls("http://c1:8123/, HTTPS://c2:8443,http://c1:8123")).containsExactly("http://c1:8123", "https://c2:8443");
    }

    @ParameterizedTest
    @ValueSource(strings = {"c1:8123", "ftp://c1", "http://", "http://c1:8123/play", "http://c1?x=1", "http://c1#top", "http://u:p@c1",
            "http://c1,,http://c2", "http://c 1"})
    void refusesWhatIsNoListOfServers(String value)
    {
        assertThat(ClickHouseSettings.urls(value)).isEmpty();
        assertThat(fields(config(ClickHouseSettings.URLS, value))).containsExactly(ClickHouseSettings.URLS);
    }

    @Test
    void needsTheServersAndTheUser()
    {
        assertThat(ClickHouseSettings.problems(config(ClickHouseSettings.URLS, UNSET, ClickHouseSettings.USER, " ")))
                .extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ClickHouseSettings.URLS, ConfigProblem.Reason.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple(ClickHouseSettings.USER, ConfigProblem.Reason.REQUIRED));
    }

    @Test
    void readsACertificateSignInOverHttps()
    {
        ServiceConfig config = config(ClickHouseSettings.URLS, "https://c1:8443", ClickHouseSettings.AUTHENTICATION, "Certificate",
                ClickHouseSettings.CLIENT_CERTIFICATE, "/etc/c.pem", ClickHouseSettings.CLIENT_KEY, "/etc/k.pem", ClickHouseSettings.ROOT_CERTIFICATE,
                "/etc/ca.pem");
        assertThat(ClickHouseSettings.problems(config)).isEmpty();
        ClickHouseSettings settings = ClickHouseSettings.of(config);
        assertThat(settings.authentication()).isEqualTo(ClickHouseSettings.CERTIFICATE_SIGN_IN);
        assertThat(settings.clientCertificate()).isEqualTo("/etc/c.pem");
        assertThat(settings.clientKey()).isEqualTo("/etc/k.pem");
        assertThat(settings.rootCertificate()).isEqualTo("/etc/ca.pem");
    }

    @Test
    void aCertificateSignInNeedsItsFilesAndHttpsAndNoPassword()
    {
        ServiceConfig config = config(ClickHouseSettings.AUTHENTICATION, ClickHouseSettings.CERTIFICATE_SIGN_IN, ClickHouseSettings.PASSWORD, "x");
        assertThat(fields(config)).containsExactlyInAnyOrder(ClickHouseSettings.PASSWORD, ClickHouseSettings.CLIENT_CERTIFICATE,
                ClickHouseSettings.CLIENT_KEY, ClickHouseSettings.URLS);
    }

    @Test
    void aPasswordSignInUsesNoCertificateFiles()
    {
        assertThat(fields(config(ClickHouseSettings.CLIENT_CERTIFICATE, "/c.pem", ClickHouseSettings.CLIENT_KEY, "/k.pem")))
                .containsExactlyInAnyOrder(ClickHouseSettings.CLIENT_CERTIFICATE, ClickHouseSettings.CLIENT_KEY);
    }

    @Test
    void refusesAnUnknownSignInAndACaCertificateWithoutHttps()
    {
        assertThat(fields(config(ClickHouseSettings.AUTHENTICATION, "kerberos"))).containsExactly(ClickHouseSettings.AUTHENTICATION);
        assertThat(fields(config(ClickHouseSettings.ROOT_CERTIFICATE, "/ca.pem"))).containsExactly(ClickHouseSettings.ROOT_CERTIFICATE);
        assertThat(fields(config(ClickHouseSettings.ROOT_CERTIFICATE, "/ca.pem", ClickHouseSettings.URLS, "http://a:1,https://b:2"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-1", "1.5", "soon", "1234567"})
    void refusesTimeoutsOutOfRange(String value)
    {
        assertThat(fields(config(ClickHouseSettings.TIMEOUT, value))).containsExactly(ClickHouseSettings.TIMEOUT);
        assertThatIllegalArgumentException().isThrownBy(() -> ClickHouseSettings.of(config(ClickHouseSettings.TIMEOUT, value)))
                .withMessageStartingWith(ClickHouseSettings.TIMEOUT + ": ");
    }

    @Test
    void readsATimeout()
    {
        assertThat(ClickHouseSettings.of(config(ClickHouseSettings.TIMEOUT, " 600000 ")).timeout()).isEqualTo(600_000);
    }
}
