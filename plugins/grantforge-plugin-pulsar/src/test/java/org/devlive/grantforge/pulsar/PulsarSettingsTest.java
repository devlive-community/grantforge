// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

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

class PulsarSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(PulsarSettings.WEB_SERVICE_URL, "http://broker1.example.com"));
        for (int index = 0; index < values.length; index += 2) {
            if (UNSET.equals(values[index + 1])) {
                map.remove(values[index]);
            }
            else {
                map.put(values[index], values[index + 1]);
            }
        }
        return new ServiceConfig("events", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return PulsarSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsABrokerWithoutSignInAndTheDefaults()
    {
        assertThat(PulsarSettings.problems(config())).isEmpty();
        PulsarSettings settings = PulsarSettings.of(config());
        assertThat(settings.webServiceUrl()).isEqualTo("http://broker1.example.com:8080");
        assertThat(settings.authentication()).isEqualTo(PulsarSettings.NONE);
        assertThat(settings.verifyHostname()).isTrue();
        assertThat(settings.timeout()).isEqualTo(PulsarSettings.DEFAULT_TIMEOUT);
        assertThat(settings.trustCerts()).isNull();
        assertThat(settings.toString()).doesNotContain("token", "password").contains("broker1");
    }

    @Test
    void turnsSeveralBrokersIntoOneServiceUrl()
    {
        assertThat(PulsarSettings.serviceUrl("http://b1:8080, http://b2:8081,b3")).isEqualTo("http://b1:8080,b2:8081,b3:8080");
        assertThat(PulsarSettings.serviceUrl("HTTPS://b1,b1:8443,[2001:db8::1]")).isEqualTo("https://b1:8443,[2001:db8::1]:8443");
        assertThat(PulsarSettings.serviceUrl("http://b1:8080/")).isEqualTo("http://b1:8080");
    }

    @ParameterizedTest
    @ValueSource(strings = {"b1:8080", "ftp://b1", "http://b1,https://b2", "http://b1:0", "http://b1:70000", "http://b1:8080/admin", "http://b1,,b2"})
    void refusesWhatIsNoListOfBrokers(String value)
    {
        assertThat(PulsarSettings.serviceUrl(value)).isNull();
        assertThat(fields(config(PulsarSettings.WEB_SERVICE_URL, value))).containsExactly(PulsarSettings.WEB_SERVICE_URL);
    }

    @Test
    void needsTheBrokers()
    {
        assertThat(PulsarSettings.problems(config(PulsarSettings.WEB_SERVICE_URL, UNSET))).singleElement()
                .satisfies(problem -> assertThat(problem.reason()).isEqualTo(ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(PulsarSettings.WEB_SERVICE_URL, " "))).containsExactly(PulsarSettings.WEB_SERVICE_URL);
    }

    @Test
    void readsATokenSignIn()
    {
        PulsarSettings settings = PulsarSettings.of(config(PulsarSettings.AUTHENTICATION, "Token", PulsarSettings.TOKEN_FIELD, " eyJ.a.b ",
                PulsarSettings.TRUST_CERTS, "/etc/ca.pem", PulsarSettings.HOSTNAME_VERIFICATION, "FALSE", PulsarSettings.TIMEOUT, "5000"));
        assertThat(settings.authentication()).isEqualTo(PulsarSettings.TOKEN);
        assertThat(settings.token()).isEqualTo("eyJ.a.b");
        assertThat(settings.trustCerts()).isEqualTo("/etc/ca.pem");
        assertThat(settings.verifyHostname()).isFalse();
        assertThat(settings.timeout()).isEqualTo(5000);
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "token"))).containsExactly(PulsarSettings.TOKEN_FIELD);
    }

    @Test
    void readsABasicSignIn()
    {
        PulsarSettings settings = PulsarSettings.of(config(PulsarSettings.AUTHENTICATION, "basic", PulsarSettings.USER, "grantforge",
                PulsarSettings.PASSWORD, "secret"));
        assertThat(settings.user()).isEqualTo("grantforge");
        assertThat(settings.password()).isEqualTo("secret");
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "basic"))).containsExactly(PulsarSettings.USER, PulsarSettings.PASSWORD);
    }

    @Test
    void readsAnOauth2SignIn()
    {
        PulsarSettings settings = PulsarSettings.of(config(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "https://auth.example.com/",
                PulsarSettings.PRIVATE_KEY, "/etc/pulsar/credentials.json", PulsarSettings.AUDIENCE, "urn:pulsar", PulsarSettings.SCOPE, "admin"));
        assertThat(String.valueOf(settings.issuerUrl())).isEqualTo("https://auth.example.com/");
        assertThat(String.valueOf(settings.credentialsUrl())).isEqualTo("file:/etc/pulsar/credentials.json");
        assertThat(settings.audience()).isEqualTo("urn:pulsar");
        assertThat(settings.scope()).isEqualTo("admin");
        assertThat(String.valueOf(PulsarSettings.of(config(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "http://auth",
                PulsarSettings.PRIVATE_KEY, "file:///etc/key.json")).credentialsUrl())).isEqualTo("file:/etc/key.json");
    }

    @Test
    void checksAnOauth2SignIn()
    {
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "oauth2"))).containsExactly(PulsarSettings.ISSUER_URL, PulsarSettings.PRIVATE_KEY);
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "ftp://auth", PulsarSettings.PRIVATE_KEY,
                "relative/key.json"))).containsExactly(PulsarSettings.ISSUER_URL, PulsarSettings.PRIVATE_KEY);
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "https:///nohost", PulsarSettings.PRIVATE_KEY,
                "data:application/json;base64,e30="))).containsExactly(PulsarSettings.ISSUER_URL, PulsarSettings.PRIVATE_KEY);
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "https://a b", PulsarSettings.PRIVATE_KEY,
                "file:"))).containsExactly(PulsarSettings.ISSUER_URL, PulsarSettings.PRIVATE_KEY);
    }

    @Test
    void readsACertificateSignIn()
    {
        PulsarSettings settings = PulsarSettings.of(config(PulsarSettings.AUTHENTICATION, "tls", PulsarSettings.CERTIFICATE, "/etc/client.pem",
                PulsarSettings.KEY, "/etc/client.key"));
        assertThat(settings.certificate()).isEqualTo("/etc/client.pem");
        assertThat(settings.key()).isEqualTo("/etc/client.key");
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "tls"))).containsExactly(PulsarSettings.CERTIFICATE, PulsarSettings.KEY);
    }

    @Test
    void refusesFieldsOfAnotherSignIn()
    {
        assertThat(fields(config(PulsarSettings.TOKEN_FIELD, "t", PulsarSettings.USER, "u"))).containsExactlyInAnyOrder(PulsarSettings.TOKEN_FIELD,
                PulsarSettings.USER);
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "token", PulsarSettings.TOKEN_FIELD, "t", PulsarSettings.CERTIFICATE, "/c")))
                .containsExactly(PulsarSettings.CERTIFICATE);
    }

    @Test
    void refusesAnUnknownSignInAndBadOptions()
    {
        assertThat(fields(config(PulsarSettings.AUTHENTICATION, "kerberos"))).containsExactly(PulsarSettings.AUTHENTICATION);
        assertThat(fields(config(PulsarSettings.HOSTNAME_VERIFICATION, "yes"))).containsExactly(PulsarSettings.HOSTNAME_VERIFICATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-1", "1000000", "soon"})
    void refusesATimeoutOutOfRange(String timeout)
    {
        assertThat(fields(config(PulsarSettings.TIMEOUT, timeout))).containsExactly(PulsarSettings.TIMEOUT);
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarSettings.of(config(PulsarSettings.TIMEOUT, timeout)))
                .withMessageStartingWith(PulsarSettings.TIMEOUT + ": ");
    }

    @Test
    void acceptsTheTimeoutsBounds()
    {
        assertThat(PulsarSettings.timeout(config(PulsarSettings.TIMEOUT, "1"))).isEqualTo(1);
        assertThat(PulsarSettings.timeout(config(PulsarSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
    }
}
