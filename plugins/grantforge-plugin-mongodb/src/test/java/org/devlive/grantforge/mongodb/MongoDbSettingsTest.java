// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MongoDbSettingsTest
{
    private static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("warehouse", map);
    }

    private static List<String> fields(String... values)
    {
        return MongoDbSettings.problems(config(values)).stream().map(ConfigProblem::field).toList();
    }

    private static final String[] SCRAM = {MongoDbSettings.URI, "mongodb://db1:27017,db2/?replicaSet=rs0", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret"};

    @Test
    void readsADeploymentWithItsDefaults()
    {
        MongoDbSettings settings = MongoDbSettings.of(config(SCRAM));
        assertThat(settings.uri()).isEqualTo("mongodb://db1:27017,db2/?replicaSet=rs0");
        assertThat(settings.mechanism()).isEqualTo(MongoDbSettings.SCRAM_SHA_256);
        assertThat(settings.source()).isEqualTo("admin");
        assertThat(settings.serviceName()).isEqualTo("mongodb");
        assertThat(settings.timeout()).isEqualTo(30_000);
        assertThat(settings.caFile()).isNull();
    }

    @Test
    void readsEverySetting()
    {
        MongoDbSettings settings = MongoDbSettings.of(config(MongoDbSettings.URI, " mongodb://db1/?tls=true ", MongoDbSettings.USER, "svc@EXAMPLE.COM",
                MongoDbSettings.MECHANISM, "gssapi", MongoDbSettings.SOURCE, "other", MongoDbSettings.CA_FILE, "/ca.pem", MongoDbSettings.CERTIFICATE_KEY_FILE, "/client.pem",
                MongoDbSettings.CERTIFICATE_KEY_PASSWORD, "key-secret", MongoDbSettings.KEYTAB, "/svc.keytab", MongoDbSettings.KDC, "kdc1:88",
                MongoDbSettings.SERVICE_NAME, "mongo", MongoDbSettings.TIMEOUT, "7000"));
        assertThat(settings).isEqualTo(new MongoDbSettings("mongodb://db1/?tls=true", "svc@EXAMPLE.COM", null, MongoDbSettings.GSSAPI, "other", "/ca.pem", "/client.pem",
                "key-secret", "/svc.keytab", "kdc1:88", "mongo", 7000));
    }

    @Test
    void wantsAConnectionStringWithoutCredentials()
    {
        assertThat(fields(MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret")).containsExactly(MongoDbSettings.URI);
        assertThat(MongoDbSettings.problems(config(MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret")).get(0).reason()).isEqualTo(ConfigProblem.Reason.REQUIRED);
        assertThat(fields(MongoDbSettings.URI, "http://db1", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret")).containsExactly(MongoDbSettings.URI);
        List<ConfigProblem> embedded = MongoDbSettings.problems(config(MongoDbSettings.URI, "mongodb://lookup:hunter2@db1/", MongoDbSettings.USER, "lookup",
                MongoDbSettings.PASSWORD, "secret"));
        assertThat(embedded).singleElement().satisfies(problem -> {
            assertThat(problem.field()).isEqualTo(MongoDbSettings.URI);
            assertThat(problem.detail()).contains("holds no credentials").doesNotContain("hunter2");
        });
    }

    @Test
    void aBrokenConnectionStringIsNotRepeated()
    {
        List<ConfigProblem> broken = MongoDbSettings.problems(config(MongoDbSettings.URI, "mongodb://lookup:hunter2@", MongoDbSettings.USER, "lookup",
                MongoDbSettings.PASSWORD, "secret"));
        assertThat(broken).singleElement().satisfies(problem -> assertThat(problem.detail()).doesNotContain("hunter2"));
    }

    @Test
    void scramAndLdapWantAUserAndPassword()
    {
        String uri = "mongodb://db1";
        assertThat(fields(MongoDbSettings.URI, uri)).containsExactly(MongoDbSettings.USER, MongoDbSettings.PASSWORD);
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "PLAIN", MongoDbSettings.USER, "lookup")).containsExactly(MongoDbSettings.PASSWORD);
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "SCRAM-SHA-1", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "")).containsExactly(MongoDbSettings.PASSWORD);
    }

    @Test
    void aClientCertificateSignsInWithoutAUser()
    {
        String uri = "mongodb://db1/?tls=true";
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "MONGODB-X509")).containsExactly(MongoDbSettings.CERTIFICATE_KEY_FILE);
        MongoDbSettings settings = MongoDbSettings.of(config(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "mongodb-x509", MongoDbSettings.CERTIFICATE_KEY_FILE, "/client.pem"));
        assertThat(settings.user()).isNull();
        assertThat(settings.password()).isNull();
    }

    @Test
    void kerberosWantsAPrincipalWithAKeytabOrPassword()
    {
        String uri = "mongodb://db1";
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "GSSAPI")).containsExactly(MongoDbSettings.USER, MongoDbSettings.PASSWORD);
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "GSSAPI", MongoDbSettings.USER, "svc", MongoDbSettings.PASSWORD, "secret")).isEmpty();
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.MECHANISM, "GSSAPI", MongoDbSettings.USER, "svc", MongoDbSettings.KEYTAB, "/svc.keytab")).isEmpty();
        assertThat(fields(MongoDbSettings.URI, uri, MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret", MongoDbSettings.KEYTAB, "/svc.keytab"))
                .containsExactly(MongoDbSettings.KEYTAB);
    }

    @Test
    void refusesAnUnknownMechanism()
    {
        assertThat(fields(MongoDbSettings.URI, "mongodb://db1", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret", MongoDbSettings.MECHANISM, "MONGODB-CR"))
                .containsExactly(MongoDbSettings.MECHANISM);
    }

    @Test
    void tlsFilesWantTlsInTheConnectionString()
    {
        assertThat(fields(MongoDbSettings.URI, "mongodb://db1", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret", MongoDbSettings.CA_FILE, "/ca.pem",
                MongoDbSettings.CERTIFICATE_KEY_FILE, "/client.pem")).containsExactly(MongoDbSettings.CA_FILE, MongoDbSettings.CERTIFICATE_KEY_FILE);
        assertThat(fields(MongoDbSettings.URI, "mongodb://db1/?tls=true", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret", MongoDbSettings.CA_FILE, "/ca.pem")).isEmpty();
    }

    @Test
    void checksTheTimeout()
    {
        assertThat(MongoDbSettings.timeout(config())).isEqualTo(30_000);
        assertThat(MongoDbSettings.timeout(config(MongoDbSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
        for (String wrong : List.of("0", "600001", "soon", "1234567")) {
            assertThatIllegalArgumentException().isThrownBy(() -> MongoDbSettings.timeout(config(MongoDbSettings.TIMEOUT, wrong)));
        }
        String[] with = {MongoDbSettings.URI, "mongodb://db1", MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD, "secret", MongoDbSettings.TIMEOUT, "soon"};
        assertThat(fields(with)).containsExactly(MongoDbSettings.TIMEOUT);
    }

    @Test
    void readingUnusableSettingsNamesTheFirstProblem()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> MongoDbSettings.of(config())).withMessageStartingWith(MongoDbSettings.URI + ": ");
    }

    @Test
    void doesNotPrintThePassword()
    {
        assertThat(MongoDbSettings.of(config(SCRAM)).toString()).contains("lookup").doesNotContain("secret");
    }
}
