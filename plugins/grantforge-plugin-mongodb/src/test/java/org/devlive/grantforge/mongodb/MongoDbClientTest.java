// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.AuthenticationMechanism;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MongoDbClientTest
{
    private static MongoDbSettings settings(String mechanism, @Nullable String user, @Nullable String password)
    {
        return new MongoDbSettings("mongodb://db1:27017,db2:27018/?replicaSet=rs0", user, password, mechanism, "sales", null, null, null, null, null, "mongodb", 7000);
    }

    private static MongoCredential credential(String mechanism, @Nullable String user, @Nullable String password)
    {
        return new MongoDbClient(settings(mechanism, user, password)).credential();
    }

    @Test
    void takesItsHostsFromTheConnectionStringAndTimeoutsFromTheSettings()
    {
        MongoClientSettings built = new MongoDbClient(settings(MongoDbSettings.SCRAM_SHA_256, "lookup", "secret")).clientSettings();
        assertThat(built.getClusterSettings().getHosts()).extracting(Object::toString).containsExactly("db1:27017", "db2:27018");
        assertThat(built.getClusterSettings().getRequiredReplicaSetName()).isEqualTo("rs0");
        assertThat(built.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(7000);
        assertThat(built.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS)).isEqualTo(7000);
        assertThat(built.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS)).isEqualTo(7000);
        assertThat(built.getSslSettings().getContext()).isNull();
    }

    @Test
    void signsInWithScramInTheUsersDatabase()
    {
        MongoCredential current = credential(MongoDbSettings.SCRAM_SHA_256, "lookup", "secret");
        assertThat(current.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SCRAM_SHA_256);
        assertThat(current.getUserName()).isEqualTo("lookup");
        assertThat(current.getSource()).isEqualTo("sales");
        assertThat(current.getPassword()).isEqualTo("secret".toCharArray());
        assertThat(credential(MongoDbSettings.SCRAM_SHA_1, "lookup", "secret").getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SCRAM_SHA_1);
    }

    @Test
    void signsInWithAClientCertificateNamedOrNot()
    {
        assertThat(credential(MongoDbSettings.X509, null, null).getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.MONGODB_X509);
        assertThat(credential(MongoDbSettings.X509, null, null).getUserName()).isNull();
        assertThat(credential(MongoDbSettings.X509, "CN=client", null).getUserName()).isEqualTo("CN=client");
    }

    @Test
    void signsInWithLdapInTheExternalDatabase()
    {
        MongoCredential plain = credential(MongoDbSettings.PLAIN, "lookup", "secret");
        assertThat(plain.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.PLAIN);
        assertThat(plain.getSource()).isEqualTo("$external");
    }

    @Test
    void reportsAKerberosSignInThatCannotStart()
    {
        MongoDbSettings keytab = new MongoDbSettings("mongodb://db1", "svc@EXAMPLE.COM", null, MongoDbSettings.GSSAPI, "admin", null, null, null, "/no/such.keytab", null,
                "mongodb", 7000);
        assertThatThrownBy(() -> new MongoDbClient(keytab).credential()).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
    }

    @Test
    void reportsAKerberosSignInThatIsRefused()
    {
        MongoDbSettings neither = new MongoDbSettings("mongodb://db1", "svc@EXAMPLE.COM", null, MongoDbSettings.GSSAPI, "admin", null, null, null, null, null, "mongodb", 7000);
        assertThatThrownBy(() -> new MongoDbClient(neither).credential()).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void usesTheTlsFilesGiven(@TempDir Path files) throws Exception
    {
        TestCertificates certificates = new TestCertificates(files);
        MongoDbSettings tls = new MongoDbSettings("mongodb://db1/?tls=true", null, null, MongoDbSettings.X509, "admin", certificates.file("ca.pem"),
                certificates.file("client-pkcs8.pem"), null, null, null, "mongodb", 7000);
        MongoClientSettings built = new MongoDbClient(tls).clientSettings();
        assertThat(built.getSslSettings().isEnabled()).isTrue();
        assertThat(built.getSslSettings().getContext()).isNotNull();
        MongoDbSettings caOnly = new MongoDbSettings("mongodb://db1/?tls=true", "lookup", "secret", MongoDbSettings.SCRAM_SHA_256, "admin", certificates.file("ca.pem"), null, null,
                null, null, "mongodb", 7000);
        assertThat(new MongoDbClient(caOnly).clientSettings().getSslSettings().getContext()).isNotNull();
        MongoDbSettings keyOnly = new MongoDbSettings("mongodb://db1/?tls=true", null, null, MongoDbSettings.X509, "admin", null, certificates.file("client-pkcs8.pem"), null, null,
                null, "mongodb", 7000);
        assertThat(new MongoDbClient(keyOnly).clientSettings().getSslSettings().getContext()).isNotNull();
    }

    @Test
    void reportsTlsFilesThatCannotBeUsed()
    {
        MongoDbSettings missing = new MongoDbSettings("mongodb://db1/?tls=true", "lookup", "secret", MongoDbSettings.SCRAM_SHA_256, "admin", "/no/such/ca.pem", null, null, null, null,
                "mongodb", 7000);
        assertThatThrownBy(() -> new MongoDbClient(missing).clientSettings()).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getMessage()).startsWith("the TLS files cannot be used"));
    }

    @Test
    void closesTheClientAfterTheWork()
    {
        MongoClient client = mock(MongoClient.class);
        MongoDbClient connected = new MongoDbClient(settings(MongoDbSettings.SCRAM_SHA_256, "lookup", "secret"))
        {
            @Override
            MongoClient connect()
            {
                return client;
            }
        };
        String done = connected.run(ignored -> "done");
        assertThat(done).isEqualTo("done");
        verify(client).close();
    }

    @Test
    void connectsLazily()
    {
        try (MongoClient client = new MongoDbClient(settings(MongoDbSettings.SCRAM_SHA_256, "lookup", "secret")).connect()) {
            assertThat(client.getClusterDescription()).isNotNull();
        }
    }
}
