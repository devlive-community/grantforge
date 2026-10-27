// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

class ElasticsearchSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    /** An API key in the form Elasticsearch returns it: the Base64 of its id and secret. */
    static final String ENCODED_KEY = Base64.getEncoder().encodeToString("example-id:example-secret".getBytes(StandardCharsets.UTF_8));

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(ElasticsearchSettings.HOSTS, "https://es1.example.com:9200", ElasticsearchSettings.USER, "lookup",
                ElasticsearchSettings.PASSWORD, "secret"));
        for (int index = 0; index < values.length; index += 2) {
            if (UNSET.equals(values[index + 1])) {
                map.remove(values[index]);
            }
            else {
                map.put(values[index], values[index + 1]);
            }
        }
        return new ServiceConfig("search", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return ElasticsearchSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsANodeWithAPasswordAndTheDefaults()
    {
        ServiceConfig config = config(ElasticsearchSettings.PASSWORD, " secret ");
        assertThat(ElasticsearchSettings.problems(config)).isEmpty();
        ElasticsearchSettings settings = ElasticsearchSettings.of(config);
        assertThat(settings.hosts()).containsExactly("https://es1.example.com:9200");
        assertThat(settings.secure()).isTrue();
        assertThat(settings.authentication()).isEqualTo(ElasticsearchSettings.PASSWORD_SIGN_IN);
        assertThat(settings.user()).isEqualTo("lookup");
        assertThat(settings.password()).as("a password is taken as it is").isEqualTo(" secret ");
        assertThat(settings.verificationMode()).isEqualTo(ElasticsearchSettings.FULL);
        assertThat(settings.certificateAuthorities()).isNull();
        assertThat(settings.timeout()).isEqualTo(ElasticsearchSettings.DEFAULT_TIMEOUT);
        assertThat(settings.toString()).doesNotContain("secret").contains("es1.example.com");
    }

    @Test
    void readsSeveralNodesOnceAsUrlsWithTheDefaultSchemeAndPort()
    {
        assertThat(ElasticsearchSettings.hosts("ES1, https://es2:9201,https://es1:9200/, https://[::1]:9202, 10.0.0.7"))
                .containsExactly("https://es1:9200", "https://es2:9201", "https://[::1]:9202", "https://10.0.0.7:9200");
        assertThat(ElasticsearchSettings.hosts("HTTP://es1")).containsExactly("http://es1:9200");
    }

    @ParameterizedTest
    @ValueSource(strings = {"es1:0", "es1:70000", "es1:", "ftp://es1", "https://es1/path", "es1,,es2", "-es1", "os 1", "user@es1", "::1"})
    void refusesWhatIsNoListOfNodes(String value)
    {
        assertThat(ElasticsearchSettings.hosts(value)).isEmpty();
        assertThat(fields(config(ElasticsearchSettings.HOSTS, value))).containsExactly(ElasticsearchSettings.HOSTS);
    }

    @Test
    void wantsNodesAndTheNodesOfOneSchemeOnly()
    {
        assertThat(ElasticsearchSettings.problems(config(ElasticsearchSettings.HOSTS, UNSET))).extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(ElasticsearchSettings.HOSTS, ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(ElasticsearchSettings.HOSTS, "http://es1,https://es2"))).containsExactly(ElasticsearchSettings.HOSTS);
    }

    @Test
    void aPasswordSignInWantsAUserAndAPassword()
    {
        assertThat(ElasticsearchSettings.problems(config(ElasticsearchSettings.USER, " ", ElasticsearchSettings.PASSWORD, UNSET)))
                .extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(ElasticsearchSettings.USER, ConfigProblem.Reason.REQUIRED), tuple(ElasticsearchSettings.PASSWORD, ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(ElasticsearchSettings.PASSWORD, ""))).containsExactly(ElasticsearchSettings.PASSWORD);
        assertThat(fields(config(ElasticsearchSettings.CERTIFICATE, "/etc/client.pem"))).containsExactly(ElasticsearchSettings.CERTIFICATE);
    }

    @Test
    void anApiKeySignInWantsTheEncodedKeyAlone()
    {
        ServiceConfig key = config(ElasticsearchSettings.AUTHENTICATION, "API_KEY", ElasticsearchSettings.USER, UNSET, ElasticsearchSettings.PASSWORD, UNSET,
                ElasticsearchSettings.API_KEY, " " + ENCODED_KEY + " ");
        assertThat(ElasticsearchSettings.problems(key)).isEmpty();
        ElasticsearchSettings settings = ElasticsearchSettings.of(key);
        assertThat(settings.authentication()).isEqualTo(ElasticsearchSettings.API_KEY_SIGN_IN);
        assertThat(settings.apiKey()).isEqualTo(ENCODED_KEY);
        assertThat(settings.toString()).doesNotContain("VnVh");
        assertThat(ElasticsearchSettings.problems(config(ElasticsearchSettings.AUTHENTICATION, "api_key", ElasticsearchSettings.USER, UNSET,
                ElasticsearchSettings.PASSWORD, UNSET))).extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(ElasticsearchSettings.API_KEY, ConfigProblem.Reason.REQUIRED));
        assertThat(ElasticsearchSettings.problems(config(ElasticsearchSettings.AUTHENTICATION, "api_key", ElasticsearchSettings.USER, UNSET,
                ElasticsearchSettings.PASSWORD, UNSET, ElasticsearchSettings.API_KEY, "my key:secret"))).extracting(ConfigProblem::field, ConfigProblem::reason)
                .as("the id and secret are not the encoded key").containsExactly(tuple(ElasticsearchSettings.API_KEY, ConfigProblem.Reason.INVALID));
        assertThat(fields(config(ElasticsearchSettings.API_KEY, "VnVh"))).as("only API key sign-ins take a key").containsExactly(ElasticsearchSettings.API_KEY);
    }

    @Test
    void aCertificateSignInWantsTheCertificateAndItsKeyOverHttps()
    {
        ServiceConfig certificate = config(ElasticsearchSettings.AUTHENTICATION, "Certificate", ElasticsearchSettings.USER, UNSET,
                ElasticsearchSettings.PASSWORD, UNSET, ElasticsearchSettings.CERTIFICATE, "/etc/client.pem", ElasticsearchSettings.KEY, "/etc/client-key.pem",
                ElasticsearchSettings.KEY_PASSPHRASE, "phrase");
        assertThat(ElasticsearchSettings.problems(certificate)).isEmpty();
        ElasticsearchSettings settings = ElasticsearchSettings.of(certificate);
        assertThat(settings.authentication()).isEqualTo(ElasticsearchSettings.CERTIFICATE_SIGN_IN);
        assertThat(settings.certificate()).isEqualTo("/etc/client.pem");
        assertThat(settings.key()).isEqualTo("/etc/client-key.pem");
        assertThat(settings.keyPassphrase()).isEqualTo("phrase");
        assertThat(settings.user()).isNull();

        assertThat(ElasticsearchSettings.problems(config(ElasticsearchSettings.AUTHENTICATION, "certificate", ElasticsearchSettings.USER, UNSET,
                ElasticsearchSettings.PASSWORD, UNSET))).extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(ElasticsearchSettings.CERTIFICATE, ConfigProblem.Reason.REQUIRED), tuple(ElasticsearchSettings.KEY, ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(ElasticsearchSettings.AUTHENTICATION, "certificate", ElasticsearchSettings.CERTIFICATE, "/c.pem", ElasticsearchSettings.KEY,
                "/k.pem"))).as("the user and password belong to password sign-ins").containsExactlyInAnyOrder(ElasticsearchSettings.USER, ElasticsearchSettings.PASSWORD);
        assertThat(fields(config(ElasticsearchSettings.HOSTS, "http://es1", ElasticsearchSettings.AUTHENTICATION, "certificate", ElasticsearchSettings.USER, UNSET,
                ElasticsearchSettings.PASSWORD, UNSET, ElasticsearchSettings.CERTIFICATE, "/c.pem", ElasticsearchSettings.KEY, "/k.pem")))
                .containsExactly(ElasticsearchSettings.HOSTS);
        assertThat(fields(config(ElasticsearchSettings.AUTHENTICATION, "kerberos"))).as("no sign-in uses the password's fields then")
                .containsExactly(ElasticsearchSettings.AUTHENTICATION, ElasticsearchSettings.USER, ElasticsearchSettings.PASSWORD);
    }

    @Test
    void checksTheVerificationMode()
    {
        assertThat(ElasticsearchSettings.of(config(ElasticsearchSettings.VERIFICATION_MODE, "Certificate", ElasticsearchSettings.CERTIFICATE_AUTHORITIES,
                "/etc/ca.pem")).verificationMode()).isEqualTo("certificate");
        assertThat(fields(config(ElasticsearchSettings.VERIFICATION_MODE, "strict"))).containsExactly(ElasticsearchSettings.VERIFICATION_MODE);
        assertThat(fields(config(ElasticsearchSettings.VERIFICATION_MODE, "none", ElasticsearchSettings.CERTIFICATE_AUTHORITIES, "/etc/ca.pem")))
                .containsExactly(ElasticsearchSettings.CERTIFICATE_AUTHORITIES);
        assertThat(fields(config(ElasticsearchSettings.HOSTS, "http://es1", ElasticsearchSettings.VERIFICATION_MODE, "full",
                ElasticsearchSettings.CERTIFICATE_AUTHORITIES, "/etc/ca.pem"))).as("plain HTTP has no certificates")
                .containsExactly(ElasticsearchSettings.VERIFICATION_MODE, ElasticsearchSettings.CERTIFICATE_AUTHORITIES);
        ElasticsearchSettings plain = ElasticsearchSettings.of(config(ElasticsearchSettings.HOSTS, "http://es1"));
        assertThat(plain.secure()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-5", "1.5", "soon", "1234567"})
    void refusesATimeoutOutsideItsRange(String value)
    {
        assertThat(fields(config(ElasticsearchSettings.TIMEOUT, value))).containsExactly(ElasticsearchSettings.TIMEOUT);
    }

    @Test
    void readsATimeout()
    {
        assertThat(ElasticsearchSettings.of(config(ElasticsearchSettings.TIMEOUT, " 600000 ")).timeout()).isEqualTo(600_000);
        assertThat(ElasticsearchSettings.of(config(ElasticsearchSettings.TIMEOUT, "1")).timeout()).isEqualTo(1);
    }

    @Test
    void unusableSettingsNameTheFirstProblem()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> ElasticsearchSettings.of(config(ElasticsearchSettings.HOSTS, UNSET, ElasticsearchSettings.TIMEOUT, "0")))
                .withMessageStartingWith(ElasticsearchSettings.HOSTS + ": ");
    }
}
