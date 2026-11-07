// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.mongodb.AuthenticationMechanism;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** What a real deployment answers is covered by the integration tests; these cover what is decided before and after it. */
class DriverMongoDbTest
{
    private static AgentConfig.Connection connection(String mechanism, @Nullable String user, @Nullable String password)
    {
        return new AgentConfig.Connection("mongodb://db1:27017,db2:27018/?replicaSet=rs0", user, password, mechanism, "sales", null, null, null, null, "mongodb", Duration.ofSeconds(7));
    }

    private static MongoCommandException refused(int code, String message)
    {
        return new MongoCommandException(new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code)).append("errmsg", new BsonString(message)),
                new ServerAddress("db1"));
    }

    @Test
    void takesItsHostsFromTheConnectionStringAndTimeoutsFromTheSettings()
    {
        MongoClientSettings built = DriverMongoDb.settings(connection(AgentConfig.SCRAM_SHA_256, "grantforge", "secret"));
        assertThat(built.getClusterSettings().getHosts()).extracting(Object::toString).containsExactly("db1:27017", "db2:27018");
        assertThat(built.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(7000);
        assertThat(built.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS)).isEqualTo(7000);
        assertThat(built.getSslSettings().getContext()).isNull();
    }

    @Test
    void signsInWithEachMechanism()
    {
        MongoCredential scram = DriverMongoDb.credential(connection(AgentConfig.SCRAM_SHA_256, "grantforge", "secret"));
        assertThat(scram.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SCRAM_SHA_256);
        assertThat(scram.getSource()).isEqualTo("sales");
        assertThat(DriverMongoDb.credential(connection(AgentConfig.SCRAM_SHA_1, "grantforge", "secret")).getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SCRAM_SHA_1);
        assertThat(DriverMongoDb.credential(connection(AgentConfig.PLAIN, "grantforge", "secret")).getSource()).isEqualTo("$external");
        assertThat(DriverMongoDb.credential(connection(AgentConfig.X509, null, null)).getUserName()).isNull();
        assertThat(DriverMongoDb.credential(connection(AgentConfig.X509, "CN=agent", null)).getUserName()).isEqualTo("CN=agent");
    }

    @Test
    void reportsAKerberosSignInThatFails()
    {
        AgentConfig.Connection keytab = new AgentConfig.Connection("mongodb://db1", "svc@EXAMPLE.COM", null, AgentConfig.GSSAPI, "admin", null, null, null, "/no/such.keytab", "mongodb",
                Duration.ofSeconds(7));
        assertThatThrownBy(() -> DriverMongoDb.credential(keytab)).isInstanceOf(MongoFailure.class).hasMessageContaining("the Kerberos sign-in of svc@EXAMPLE.COM failed");
    }

    @Test
    void usesTheTlsFilesGiven(@TempDir Path files) throws Exception
    {
        TestCertificates certificates = new TestCertificates(files);
        AgentConfig.Connection both = new AgentConfig.Connection("mongodb://db1/?tls=true", null, null, AgentConfig.X509, "admin", certificates.file("ca.pem"),
                certificates.file("client-pkcs8.pem"), null, null, "mongodb", Duration.ofSeconds(7));
        assertThat(DriverMongoDb.settings(both).getSslSettings().getContext()).isNotNull();
        AgentConfig.Connection keyOnly = new AgentConfig.Connection("mongodb://db1/?tls=true", null, null, AgentConfig.X509, "admin", null, certificates.file("client-pkcs8.pem"), null,
                null, "mongodb", Duration.ofSeconds(7));
        assertThat(DriverMongoDb.settings(keyOnly).getSslSettings().getContext()).isNotNull();
        AgentConfig.Connection caOnly = new AgentConfig.Connection("mongodb://db1/?tls=true", "a", "b", AgentConfig.SCRAM_SHA_256, "admin", certificates.file("ca.pem"), null, null, null,
                "mongodb", Duration.ofSeconds(7));
        assertThat(DriverMongoDb.settings(caOnly).getSslSettings().getContext()).isNotNull();
    }

    @Test
    void reportsTlsFilesThatCannotBeUsed()
    {
        AgentConfig.Connection missing = new AgentConfig.Connection("mongodb://db1/?tls=true", "a", "b", AgentConfig.SCRAM_SHA_256, "admin", "/no/such/ca.pem", null, null, null, "mongodb",
                Duration.ofSeconds(7));
        assertThatThrownBy(() -> DriverMongoDb.settings(missing)).isInstanceOf(MongoFailure.class).hasMessageStartingWith("the TLS files cannot be used");
    }

    @Test
    void tellsRefusalsFromServersNoOneCanReach()
    {
        assertThat(DriverMongoDb.failure(new MongoTimeoutException("no server")).unreachable()).isTrue();
        assertThat(DriverMongoDb.failure(new MongoSocketOpenException("closed", new ServerAddress("db1"), new IOException("closed"))).unreachable()).isTrue();
        MongoFailure refusal = DriverMongoDb.failure(refused(13, "not authorized on sales to execute command { find: \"x\" }"));
        assertThat(refusal.unreachable()).isFalse();
        assertThat(refusal).hasMessage("not authorized on sales");
        assertThat(DriverMongoDb.failure(new MongoSecurityException(MongoCredential.createScramSha256Credential("a", "admin", new char[] {'b'}), "x", refused(18, "Authentication failed.")))
                .getMessage()).isEqualTo("MongoDB refused the sign-in: Authentication failed.");
        assertThat(DriverMongoDb.failure(new MongoException("odd")).getMessage()).isEqualTo("odd");
    }

    @Test
    void closesTheClient()
    {
        MongoClient client = mock(MongoClient.class);
        new DriverMongoDb(client).close();
        verify(client).close();
    }

    @Test
    void connectsLazilyAndReportsWhatCannotBeReached()
    {
        AgentConfig.Connection closed = new AgentConfig.Connection("mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=100", "a", "b", AgentConfig.SCRAM_SHA_256, "admin", null, null, null, null,
                "mongodb", Duration.ofMillis(100));
        try (DriverMongoDb mongo = new DriverMongoDb(closed)) {
            assertThatThrownBy(mongo::databases).isInstanceOfSatisfying(MongoFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
            assertThatThrownBy(() -> mongo.collections("sales")).isInstanceOf(MongoFailure.class);
            assertThatThrownBy(() -> mongo.command("admin", new Document("hello", 1))).isInstanceOf(MongoFailure.class);
            assertThatThrownBy(() -> mongo.profile("sales", Instant.EPOCH, Instant.now(), 1)).isInstanceOf(MongoFailure.class);
        }
    }
}
