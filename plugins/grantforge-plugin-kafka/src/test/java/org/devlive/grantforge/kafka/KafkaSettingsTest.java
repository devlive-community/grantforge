// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.common.config.types.Password;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaSettingsTest
{
    @AfterEach
    void forgetRealms()
    {
        KerberosRealms.forget();
    }

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>();
        map.put(KafkaSettings.SERVERS, "kafka1:9092, kafka2:9093");
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("events", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return KafkaSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void plaintextNeedsNothingButServers()
    {
        assertThat(KafkaSettings.problems(config())).isEmpty();
        KafkaSettings settings = KafkaSettings.of(config());
        assertThat(settings.servers()).isEqualTo("kafka1:9092,kafka2:9093");
        assertThat(settings.protocol()).isEqualTo("PLAINTEXT");
        assertThat(settings.mechanism()).isNull();
        assertThat(settings.timeout()).isEqualTo(30_000);
        assertThat(settings.serviceName()).isEqualTo("kafka");
        assertThat(settings.verifyHostname()).isTrue();
        assertThat(settings.kerberos()).isFalse();
    }

    @Test
    void namesWhatIsWrong()
    {
        assertThat(fields(config(KafkaSettings.SERVERS, "kafka1"))).containsExactly(KafkaSettings.SERVERS);
        assertThat(fields(config(KafkaSettings.SERVERS, "kafka1:70000"))).containsExactly(KafkaSettings.SERVERS);
        assertThat(fields(config(KafkaSettings.SERVERS, "[::1]:9092,kafka.example.com:9092"))).isEmpty();
        assertThat(fields(config(KafkaSettings.PROTOCOL, "tls"))).containsExactly(KafkaSettings.PROTOCOL);
        assertThat(fields(config(KafkaSettings.USER, "alice", KafkaSettings.KEYTAB, "/k"))).containsExactly(KafkaSettings.USER,
                KafkaSettings.KEYTAB);
        assertThat(fields(config(KafkaSettings.TRUSTSTORE, "/t.jks"))).containsExactly(KafkaSettings.TRUSTSTORE);
        assertThat(fields(config(KafkaSettings.TRUSTSTORE_TYPE, "BKS"))).containsExactly(KafkaSettings.TRUSTSTORE_TYPE);
        assertThat(fields(config(KafkaSettings.VERIFY_HOSTNAME, "maybe"))).containsExactly(KafkaSettings.VERIFY_HOSTNAME);
        assertThat(fields(config(KafkaSettings.TIMEOUT, "0"))).containsExactly(KafkaSettings.TIMEOUT);
        assertThat(fields(config(KafkaSettings.TIMEOUT, "soon"))).containsExactly(KafkaSettings.TIMEOUT);
        assertThat(KafkaSettings.timeout(config(KafkaSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
        assertThatThrownBy(() -> KafkaSettings.of(config(KafkaSettings.PROTOCOL, "tls"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.protocol");
        assertThatThrownBy(() -> KafkaSettings.of(new ServiceConfig("x", Map.of()))).hasMessageContaining("bootstrap.servers");
    }

    @Test
    void saslNeedsAMechanismAUserAndItsSecret()
    {
        assertThat(fields(config(KafkaSettings.PROTOCOL, "SASL_SSL"))).containsExactly(KafkaSettings.MECHANISM);
        assertThat(fields(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "OAUTHBEARER")))
                .containsExactly(KafkaSettings.MECHANISM);
        assertThat(fields(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "scram-sha-512")))
                .containsExactly(KafkaSettings.USER, KafkaSettings.PASSWORD);
        assertThat(fields(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "PLAIN", KafkaSettings.USER, "alice",
                KafkaSettings.PASSWORD, "secret", KafkaSettings.KDC, "kdc:88"))).containsExactly(KafkaSettings.KDC);
        assertThat(fields(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI", KafkaSettings.USER, "alice")))
                .containsExactly(KafkaSettings.USER, KafkaSettings.PASSWORD);
        assertThat(fields(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI", KafkaSettings.USER,
                "alice@EXAMPLE.COM", KafkaSettings.KEYTAB, "/k", KafkaSettings.KDC, "kdc:not-a-port"))).containsExactly(KafkaSettings.KDC);
        KafkaSettings scram = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "sasl_ssl", KafkaSettings.MECHANISM, "SCRAM-SHA-256",
                KafkaSettings.USER, "alice", KafkaSettings.PASSWORD, "pa\"ss\\word", KafkaSettings.VERIFY_HOSTNAME, "false"));
        assertThat(scram.protocol()).isEqualTo("SASL_SSL");
        assertThat(scram.verifyHostname()).isFalse();
        assertThat(scram.jaas()).isEqualTo("org.apache.kafka.common.security.scram.ScramLoginModule required username=\"alice\""
                + " password=\"pa\\\"ss\\\\word\";");
    }

    @Test
    void writesTheJaasEntryOfEachSignIn()
    {
        KafkaSettings plain = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "PLAIN",
                KafkaSettings.USER, "alice", KafkaSettings.PASSWORD, "secret"));
        assertThat(plain.jaas()).startsWith("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"alice\"");
        KafkaSettings keytab = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI",
                KafkaSettings.USER, "grantforge@EXAMPLE.COM", KafkaSettings.KEYTAB, "/etc/gf.keytab"));
        assertThat(keytab.kerberos()).isTrue();
        assertThat(keytab.jaas()).contains("useKeyTab=true").contains("keyTab=\"/etc/gf.keytab\"").contains("principal=\"grantforge@EXAMPLE.COM\"")
                .doesNotContain(KafkaSettings.PASSWORD_OPTION);
        KafkaSettings password = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI",
                KafkaSettings.USER, "grantforge@EXAMPLE.COM", KafkaSettings.PASSWORD, "s3cret"));
        assertThat(password.jaas()).contains("useKeyTab=false").contains(KafkaSettings.PASSWORD_OPTION + "=\"s3cret\"");
        assertThat(KafkaSettings.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    }

    @Test
    void buildsTheClientSettings() throws IOException
    {
        Map<String, Object> plain = KafkaSettings.of(config()).client(KerberosPasswordHandler.class, "c-1");
        assertThat(plain).containsEntry("bootstrap.servers", "kafka1:9092,kafka2:9093").containsEntry("security.protocol", "PLAINTEXT")
                .containsEntry("client.id", "c-1").containsEntry("metric.reporters", "").containsEntry("request.timeout.ms", 30_000)
                .doesNotContainKeys("sasl.mechanism", "sasl.jaas.config", "ssl.endpoint.identification.algorithm");

        Map<String, Object> tls = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SSL", KafkaSettings.TRUSTSTORE, "/t.p12",
                KafkaSettings.TRUSTSTORE_PASSWORD, "tp", KafkaSettings.TRUSTSTORE_TYPE, "pkcs12", KafkaSettings.KEYSTORE, "/k.p12",
                KafkaSettings.KEYSTORE_PASSWORD, "kp", KafkaSettings.KEY_PASSWORD, "keyp")).client(KerberosPasswordHandler.class, "c-2");
        assertThat(tls).containsEntry("ssl.truststore.location", "/t.p12").containsEntry("ssl.truststore.type", "PKCS12")
                .containsEntry("ssl.keystore.location", "/k.p12").containsEntry("ssl.endpoint.identification.algorithm", "https")
                .containsEntry("ssl.truststore.password", new Password("tp")).containsEntry("ssl.key.password", new Password("keyp"));
        Map<String, Object> jdkTrust = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SSL", KafkaSettings.VERIFY_HOSTNAME, "false"))
                .client(KerberosPasswordHandler.class, "c-3");
        assertThat(jdkTrust).containsEntry("ssl.endpoint.identification.algorithm", "").doesNotContainKeys("ssl.truststore.location",
                "ssl.truststore.type");

        Map<String, Object> scram = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM,
                "SCRAM-SHA-512", KafkaSettings.USER, "alice", KafkaSettings.PASSWORD, "secret")).client(KerberosPasswordHandler.class, "c-4");
        assertThat(scram).containsEntry("sasl.mechanism", "SCRAM-SHA-512").containsKey("sasl.jaas.config")
                .doesNotContainKeys("sasl.kerberos.service.name", "sasl.login.callback.handler.class");
        assertThat(scram.get("sasl.jaas.config")).isInstanceOf(Password.class).hasToString(Password.HIDDEN);
    }

    @Test
    void kerberosDeclaresTheRealmAndAnswersPasswordPrompts() throws IOException
    {
        Map<String, Object> password = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI",
                KafkaSettings.USER, "grantforge@EXAMPLE.COM", KafkaSettings.PASSWORD, "s3cret", KafkaSettings.KDC, "kdc.example.com:88",
                KafkaSettings.SERVICE_NAME, "broker")).client(KerberosPasswordHandler.class, "c-5");
        assertThat(password).containsEntry("sasl.kerberos.service.name", "broker")
                .containsEntry("sasl.login.callback.handler.class", KerberosPasswordHandler.class);
        String krb5 = System.getProperty(KerberosRealms.CONFIGURATION);
        assertThat(krb5).isNotNull();
        assertThat(Files.readString(Path.of(krb5))).contains("EXAMPLE.COM").contains("kdc.example.com:88");
        Map<String, Object> keytab = KafkaSettings.of(config(KafkaSettings.PROTOCOL, "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI",
                KafkaSettings.USER, "grantforge@EXAMPLE.COM", KafkaSettings.KEYTAB, "/k")).client(KerberosPasswordHandler.class, "c-6");
        assertThat(keytab).doesNotContainKey("sasl.login.callback.handler.class").containsEntry("sasl.kerberos.service.name", "kafka");
    }
}
