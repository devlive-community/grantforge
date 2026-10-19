// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TrinoSettingsTest
{
    private static final String URL = "jdbc:trino://trino.example.com:8443";
    /** A value that removes the field. */
    private static final String UNSET = "<unset>";

    private static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(TrinoSettings.URL, URL, TrinoSettings.USER, "grantforge"));
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
        return TrinoSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    private static List<String> details(ServiceConfig config)
    {
        return TrinoSettings.problems(config).stream().map(ConfigProblem::detail).toList();
    }

    @Test
    void acceptsAPlainService()
    {
        assertThat(TrinoSettings.problems(config())).isEmpty();
        TrinoSettings settings = TrinoSettings.of(config());
        assertThat(settings.authentication()).isEqualTo(TrinoSettings.NONE);
        assertThat(settings.tls()).isFalse();
        assertThat(settings.timeout()).isEqualTo(TrinoSettings.DEFAULT_TIMEOUT);
        assertThat(settings.kerberos()).isFalse();
        Properties driver = settings.driver();
        assertThat(driver).containsEntry("user", "grantforge").containsEntry("source", "grantforge").containsEntry("timeout", "30000ms")
                .doesNotContainKeys("password", "accessToken", "SSL", "KerberosRemoteServiceName");
    }

    @Test
    void requiresTheUrlAndTheUser()
    {
        ServiceConfig config = config(TrinoSettings.URL, UNSET, TrinoSettings.USER, " ");
        assertThat(TrinoSettings.problems(config)).extracting(ConfigProblem::field, ConfigProblem::reason).containsExactly(
                tuple(TrinoSettings.URL, ConfigProblem.Reason.REQUIRED),
                tuple(TrinoSettings.USER, ConfigProblem.Reason.REQUIRED));
        assertThatThrownBy(() -> TrinoSettings.of(config)).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("jdbc.url: ");
    }

    @Test
    void checksTheUrl()
    {
        assertThat(details(config(TrinoSettings.URL, "jdbc:presto://trino:8080"))).singleElement().asString().startsWith("the URL starts with");
        assertThat(details(config(TrinoSettings.URL, "jdbc:trino://bad host:8080"))).singleElement().asString().startsWith("the URL is not valid");
        assertThat(details(config(TrinoSettings.URL, "jdbc:trino:///catalog"))).containsExactly("the URL names no host");
        assertThat(TrinoSettings.problems(config(TrinoSettings.URL, URL + "/tpch/tiny?"))).isEmpty();
        assertThat(TrinoSettings.problems(config(TrinoSettings.URL, URL + "?clientTags=a&applicationNamePrefix=b"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user=admin", "PASSWORD=x", "accessToken=t", "sessionUser=root", "extraCredentials=a:b", "externalAuthentication=true",
            "KerberosKeytabPath=/etc/krb5.keytab", "KerberosConfigPath=/etc/krb5.conf", "KerberosDelegation=true", "SSLKeyStorePath=/k",
            "SSLTrustStorePassword=x", "kerberosPrincipal"})
    void refusesUrlParametersThatSignInOrReadSecrets(String parameter)
    {
        String name = parameter.contains("=") ? parameter.substring(0, parameter.indexOf('=')) : parameter;
        assertThat(details(config(TrinoSettings.URL, URL + "?SSL=true&" + parameter))).containsExactly("the URL may not set " + name
                + "; use the service's fields");
    }

    @Test
    void signsInOverTlsOnly()
    {
        assertThat(details(config(TrinoSettings.AUTHENTICATION, "password", TrinoSettings.PASSWORD, "secret")))
                .containsExactly("Trino signs users in over TLS only; set SSL to true");
        assertThat(TrinoSettings.problems(config(TrinoSettings.AUTHENTICATION, "PASSWORD", TrinoSettings.PASSWORD, "secret", TrinoSettings.SSL,
                "TRUE"))).isEmpty();
        assertThat(TrinoSettings.problems(config(TrinoSettings.URL, URL + "?SSL=true", TrinoSettings.AUTHENTICATION, "password",
                TrinoSettings.PASSWORD, "secret"))).as("TLS may come from the URL").isEmpty();
        assertThat(TrinoSettings.of(config(TrinoSettings.URL, URL + "?ssl=TRUE")).tls()).isTrue();
    }

    @Test
    void checksTheAuthenticationAndTheSsl()
    {
        assertThat(fields(config(TrinoSettings.AUTHENTICATION, "ldap"))).containsExactly(TrinoSettings.AUTHENTICATION);
        assertThat(details(config(TrinoSettings.SSL, "yes"))).containsExactly("SSL is true or false");
    }

    @Test
    void readsAPasswordSignIn()
    {
        assertThat(details(config(TrinoSettings.AUTHENTICATION, "password", TrinoSettings.SSL, "true")))
                .containsExactly("password authentication needs the user's password");
        TrinoSettings settings = TrinoSettings.of(config(TrinoSettings.AUTHENTICATION, "password", TrinoSettings.PASSWORD, "secret", TrinoSettings.SSL,
                "true", TrinoSettings.TIMEOUT, "5000"));
        assertThat(settings.driver()).containsEntry("password", "secret").containsEntry("SSL", "true").containsEntry("SSLVerification", "FULL")
                .containsEntry("timeout", "5000ms").doesNotContainKeys("accessToken", "SSLTrustStorePath", "SSLTrustStorePassword");
    }

    @Test
    void readsATokenSignIn()
    {
        assertThat(details(config(TrinoSettings.AUTHENTICATION, "jwt", TrinoSettings.SSL, "true"))).containsExactly("jwt authentication needs the token");
        TrinoSettings settings = TrinoSettings.of(config(TrinoSettings.AUTHENTICATION, "jwt", TrinoSettings.ACCESS_TOKEN, "eyJ.t.s", TrinoSettings.SSL,
                "true"));
        assertThat(settings.driver()).containsEntry("accessToken", "eyJ.t.s").doesNotContainKey("password");
    }

    @Test
    void refusesFieldsOfAnotherSignIn()
    {
        assertThat(details(config(TrinoSettings.PASSWORD, "secret", TrinoSettings.ACCESS_TOKEN, "t", TrinoSettings.KEYTAB, "/k",
                TrinoSettings.KDC, "kdc", TrinoSettings.SERVICE_NAME, "trino", TrinoSettings.CANONICAL_HOST, "false"))).containsExactlyInAnyOrder(
                "none authentication does not use password", "none authentication does not use accessToken", "none authentication does not use keytab",
                "none authentication does not use kerberos.kdc", "none authentication does not use KerberosRemoteServiceName",
                "none authentication does not use KerberosUseCanonicalHostname");
        assertThat(fields(config(TrinoSettings.AUTHENTICATION, "jwt", TrinoSettings.SSL, "true", TrinoSettings.ACCESS_TOKEN, "t",
                TrinoSettings.PASSWORD, "secret"))).containsExactly(TrinoSettings.PASSWORD);
    }

    @Test
    void readsAKerberosSignIn()
    {
        ServiceConfig config = config(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER, "grantforge@EXAMPLE.COM",
                TrinoSettings.KEYTAB, "/etc/grantforge/trino.keytab", TrinoSettings.KDC, "kdc.example.com:88");
        assertThat(TrinoSettings.problems(config)).isEmpty();
        TrinoSettings settings = TrinoSettings.of(config);
        assertThat(settings.kerberos()).isTrue();
        assertThat(settings.serviceName()).isEqualTo("trino");
        assertThat(settings.canonicalHost()).isTrue();
        assertThat(settings.driver()).containsEntry("user", "grantforge").containsEntry("KerberosRemoteServiceName", "trino")
                .containsEntry("KerberosUseCanonicalHostname", "true").doesNotContainKey("password");
        TrinoSettings alias = TrinoSettings.of(config(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER,
                "grantforge@EXAMPLE.COM", TrinoSettings.PASSWORD, "secret", TrinoSettings.SERVICE_NAME, "presto", TrinoSettings.CANONICAL_HOST, "FALSE"));
        assertThat(alias.driver()).containsEntry("KerberosRemoteServiceName", "presto").containsEntry("KerberosUseCanonicalHostname", "false")
                .doesNotContainKey("password");
    }

    @Test
    void checksAKerberosSignIn()
    {
        assertThat(fields(config(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true"))).containsExactly(TrinoSettings.USER,
                TrinoSettings.PASSWORD);
        assertThat(fields(config(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER, "a@EXAMPLE.COM",
                TrinoSettings.PASSWORD, "secret", TrinoSettings.KDC, "kdc:port", TrinoSettings.CANONICAL_HOST, "maybe"))).containsExactly(
                TrinoSettings.CANONICAL_HOST, TrinoSettings.KDC);
        assertThat(fields(config(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER, " ",
                TrinoSettings.KEYTAB, "/k"))).containsExactly(TrinoSettings.USER);
    }

    @Test
    void readsTheTruststore()
    {
        TrinoSettings settings = TrinoSettings.of(config(TrinoSettings.SSL, "true", TrinoSettings.TRUSTSTORE, "/etc/trust.p12",
                TrinoSettings.TRUSTSTORE_PASSWORD, "trust", TrinoSettings.TRUSTSTORE_TYPE, "pkcs12", TrinoSettings.VERIFICATION, "ca"));
        assertThat(settings.driver()).containsEntry("SSLTrustStorePath", "/etc/trust.p12").containsEntry("SSLTrustStorePassword", "trust")
                .containsEntry("SSLTrustStoreType", "PKCS12").containsEntry("SSLVerification", "CA");
        assertThat(TrinoSettings.of(config(TrinoSettings.SSL, "true", TrinoSettings.TRUSTSTORE, "/etc/trust.jks")).driver())
                .containsEntry("SSLTrustStoreType", "JKS").doesNotContainKey("SSLTrustStorePassword");
        assertThat(TrinoSettings.of(config(TrinoSettings.SSL, "true", TrinoSettings.TRUSTSTORE_PASSWORD, "changeit")).driver())
                .containsEntry("SSLTrustStorePassword", "changeit").doesNotContainKeys("SSLTrustStorePath", "SSLTrustStoreType");
    }

    @Test
    void checksTheTruststore()
    {
        assertThat(details(config(TrinoSettings.TRUSTSTORE, "/t", TrinoSettings.TRUSTSTORE_PASSWORD, "p", TrinoSettings.VERIFICATION, "FULL")))
                .containsExactly("only TLS uses SSLTrustStorePath", "only TLS uses SSLTrustStorePassword", "only TLS uses SSLVerification");
        assertThat(details(config(TrinoSettings.SSL, "true", TrinoSettings.TRUSTSTORE_TYPE, "PEM", TrinoSettings.VERIFICATION, "LOOSE")))
                .containsExactly("SSLTrustStoreType is JKS or PKCS12", "SSLVerification is FULL, CA or NONE");
        assertThat(fields(config(TrinoSettings.SSL, "true", TrinoSettings.VERIFICATION, "none", TrinoSettings.TRUSTSTORE, "/t",
                TrinoSettings.TRUSTSTORE_PASSWORD, "p"))).containsExactly(TrinoSettings.TRUSTSTORE, TrinoSettings.TRUSTSTORE_PASSWORD);
        assertThat(TrinoSettings.problems(config(TrinoSettings.SSL, "true", TrinoSettings.VERIFICATION, "NONE"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-5", "1.5", "soon", "1234567"})
    void refusesAWrongTimeout(String timeout)
    {
        assertThat(fields(config(TrinoSettings.TIMEOUT, timeout))).containsExactly(TrinoSettings.TIMEOUT);
    }

    @Test
    void readsTheTimeout()
    {
        assertThat(TrinoSettings.timeout(config(TrinoSettings.TIMEOUT, "1"))).isEqualTo(1);
        assertThat(TrinoSettings.timeout(config(TrinoSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
        assertThat(TrinoSettings.timeout(config(TrinoSettings.TIMEOUT, " "))).isEqualTo(TrinoSettings.DEFAULT_TIMEOUT);
    }

    @Test
    void mapsAPrincipalToItsSessionUser()
    {
        assertThat(TrinoSettings.sessionUser("grantforge@EXAMPLE.COM")).isEqualTo("grantforge");
        assertThat(TrinoSettings.sessionUser("trino/host@EXAMPLE.COM")).isEqualTo("trino/host");
        assertThat(TrinoSettings.sessionUser("plain")).isEqualTo("plain");
    }
}
