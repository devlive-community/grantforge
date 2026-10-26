// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

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

class OpenSearchSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(OpenSearchSettings.HOSTS, "https://os1.example.com:9200", OpenSearchSettings.USER, "lookup",
                OpenSearchSettings.PASSWORD, "secret"));
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
        return OpenSearchSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsANodeWithAPasswordAndTheDefaults()
    {
        ServiceConfig config = config(OpenSearchSettings.PASSWORD, " secret ");
        assertThat(OpenSearchSettings.problems(config)).isEmpty();
        OpenSearchSettings settings = OpenSearchSettings.of(config);
        assertThat(settings.hosts()).containsExactly("https://os1.example.com:9200");
        assertThat(settings.secure()).isTrue();
        assertThat(settings.authentication()).isEqualTo(OpenSearchSettings.PASSWORD_SIGN_IN);
        assertThat(settings.user()).isEqualTo("lookup");
        assertThat(settings.password()).as("a password is taken as it is").isEqualTo(" secret ");
        assertThat(settings.verificationMode()).isEqualTo(OpenSearchSettings.FULL);
        assertThat(settings.certificateAuthorities()).isNull();
        assertThat(settings.timeout()).isEqualTo(OpenSearchSettings.DEFAULT_TIMEOUT);
        assertThat(settings.toString()).doesNotContain("secret").contains("os1.example.com");
    }

    @Test
    void readsSeveralNodesOnceAsUrlsWithTheDefaultSchemeAndPort()
    {
        assertThat(OpenSearchSettings.hosts("OS1, https://os2:9201,https://os1:9200/, https://[::1]:9202, 10.0.0.7"))
                .containsExactly("https://os1:9200", "https://os2:9201", "https://[::1]:9202", "https://10.0.0.7:9200");
        assertThat(OpenSearchSettings.hosts("HTTP://os1")).containsExactly("http://os1:9200");
    }

    @ParameterizedTest
    @ValueSource(strings = {"os1:0", "os1:70000", "os1:", "ftp://os1", "https://os1/path", "os1,,os2", "-os1", "os 1", "user@os1", "::1"})
    void refusesWhatIsNoListOfNodes(String value)
    {
        assertThat(OpenSearchSettings.hosts(value)).isEmpty();
        assertThat(fields(config(OpenSearchSettings.HOSTS, value))).containsExactly(OpenSearchSettings.HOSTS);
    }

    @Test
    void wantsNodesAndTheNodesOfOneSchemeOnly()
    {
        assertThat(OpenSearchSettings.problems(config(OpenSearchSettings.HOSTS, UNSET))).extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(OpenSearchSettings.HOSTS, ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(OpenSearchSettings.HOSTS, "http://os1,https://os2"))).containsExactly(OpenSearchSettings.HOSTS);
    }

    @Test
    void aPasswordSignInWantsAUserAndAPassword()
    {
        assertThat(OpenSearchSettings.problems(config(OpenSearchSettings.USER, " ", OpenSearchSettings.PASSWORD, UNSET)))
                .extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(OpenSearchSettings.USER, ConfigProblem.Reason.REQUIRED), tuple(OpenSearchSettings.PASSWORD, ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(OpenSearchSettings.PASSWORD, ""))).containsExactly(OpenSearchSettings.PASSWORD);
        assertThat(fields(config(OpenSearchSettings.CERTIFICATE, "/etc/client.pem"))).containsExactly(OpenSearchSettings.CERTIFICATE);
    }

    @Test
    void aCertificateSignInWantsTheCertificateAndItsKeyOverHttps()
    {
        ServiceConfig certificate = config(OpenSearchSettings.AUTHENTICATION, "Certificate", OpenSearchSettings.USER, UNSET,
                OpenSearchSettings.PASSWORD, UNSET, OpenSearchSettings.CERTIFICATE, "/etc/client.pem", OpenSearchSettings.KEY, "/etc/client-key.pem",
                OpenSearchSettings.KEY_PASSPHRASE, "phrase");
        assertThat(OpenSearchSettings.problems(certificate)).isEmpty();
        OpenSearchSettings settings = OpenSearchSettings.of(certificate);
        assertThat(settings.authentication()).isEqualTo(OpenSearchSettings.CERTIFICATE_SIGN_IN);
        assertThat(settings.certificate()).isEqualTo("/etc/client.pem");
        assertThat(settings.key()).isEqualTo("/etc/client-key.pem");
        assertThat(settings.keyPassphrase()).isEqualTo("phrase");
        assertThat(settings.user()).isNull();

        assertThat(OpenSearchSettings.problems(config(OpenSearchSettings.AUTHENTICATION, "certificate", OpenSearchSettings.USER, UNSET,
                OpenSearchSettings.PASSWORD, UNSET))).extracting(ConfigProblem::field, ConfigProblem::reason)
                .containsExactly(tuple(OpenSearchSettings.CERTIFICATE, ConfigProblem.Reason.REQUIRED), tuple(OpenSearchSettings.KEY, ConfigProblem.Reason.REQUIRED));
        assertThat(fields(config(OpenSearchSettings.AUTHENTICATION, "certificate", OpenSearchSettings.CERTIFICATE, "/c.pem", OpenSearchSettings.KEY,
                "/k.pem"))).as("the user and password belong to password sign-ins").containsExactlyInAnyOrder(OpenSearchSettings.USER, OpenSearchSettings.PASSWORD);
        assertThat(fields(config(OpenSearchSettings.HOSTS, "http://os1", OpenSearchSettings.AUTHENTICATION, "certificate", OpenSearchSettings.USER, UNSET,
                OpenSearchSettings.PASSWORD, UNSET, OpenSearchSettings.CERTIFICATE, "/c.pem", OpenSearchSettings.KEY, "/k.pem")))
                .containsExactly(OpenSearchSettings.HOSTS);
        assertThat(fields(config(OpenSearchSettings.AUTHENTICATION, "kerberos"))).as("no sign-in uses the password's fields then")
                .containsExactly(OpenSearchSettings.AUTHENTICATION, OpenSearchSettings.USER, OpenSearchSettings.PASSWORD);
    }

    @Test
    void checksTheVerificationMode()
    {
        assertThat(OpenSearchSettings.of(config(OpenSearchSettings.VERIFICATION_MODE, "Certificate", OpenSearchSettings.CERTIFICATE_AUTHORITIES,
                "/etc/ca.pem")).verificationMode()).isEqualTo("certificate");
        assertThat(fields(config(OpenSearchSettings.VERIFICATION_MODE, "strict"))).containsExactly(OpenSearchSettings.VERIFICATION_MODE);
        assertThat(fields(config(OpenSearchSettings.VERIFICATION_MODE, "none", OpenSearchSettings.CERTIFICATE_AUTHORITIES, "/etc/ca.pem")))
                .containsExactly(OpenSearchSettings.CERTIFICATE_AUTHORITIES);
        assertThat(fields(config(OpenSearchSettings.HOSTS, "http://os1", OpenSearchSettings.VERIFICATION_MODE, "full",
                OpenSearchSettings.CERTIFICATE_AUTHORITIES, "/etc/ca.pem"))).as("plain HTTP has no certificates")
                .containsExactly(OpenSearchSettings.VERIFICATION_MODE, OpenSearchSettings.CERTIFICATE_AUTHORITIES);
        OpenSearchSettings plain = OpenSearchSettings.of(config(OpenSearchSettings.HOSTS, "http://os1"));
        assertThat(plain.secure()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-5", "1.5", "soon", "1234567"})
    void refusesATimeoutOutsideItsRange(String value)
    {
        assertThat(fields(config(OpenSearchSettings.TIMEOUT, value))).containsExactly(OpenSearchSettings.TIMEOUT);
    }

    @Test
    void readsATimeout()
    {
        assertThat(OpenSearchSettings.of(config(OpenSearchSettings.TIMEOUT, " 600000 ")).timeout()).isEqualTo(600_000);
        assertThat(OpenSearchSettings.of(config(OpenSearchSettings.TIMEOUT, "1")).timeout()).isEqualTo(1);
    }

    @Test
    void unusableSettingsNameTheFirstProblem()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> OpenSearchSettings.of(config(OpenSearchSettings.HOSTS, UNSET, OpenSearchSettings.TIMEOUT, "0")))
                .withMessageStartingWith(OpenSearchSettings.HOSTS + ": ");
    }
}
