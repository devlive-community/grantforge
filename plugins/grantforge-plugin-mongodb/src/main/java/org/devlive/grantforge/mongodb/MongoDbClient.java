// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Talks to a deployment with the official driver: one client per call, as lookups are rare and each service's settings
 * can change between them. The driver follows the replica set or the {@code mongos} list of the connection string on
 * its own.
 */
class MongoDbClient
{
    private final MongoDbSettings settings;

    MongoDbClient(MongoDbSettings settings)
    {
        this.settings = settings;
    }

    /**
     * Work on a connected client.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param client the client
         * @return the result
         */
        T run(MongoClient client);
    }

    /**
     * Connects, does some work and disconnects.
     *
     * @param <T> what the work returns
     * @param work the work
     * @return what it returned
     * @throws LookupException when a TLS file or the Kerberos sign-in fails before anything reaches MongoDB
     * @throws com.mongodb.MongoException when MongoDB cannot be reached, refuses the sign-in or fails a command
     */
    <T> T run(Work<T> work)
    {
        try (MongoClient client = connect()) {
            return work.run(client);
        }
    }

    /**
     * Connects.
     *
     * @return the client
     */
    MongoClient connect()
    {
        return MongoClients.create(clientSettings());
    }

    /**
     * Builds the driver's settings.
     *
     * @return them
     * @throws LookupException when a TLS file or the Kerberos sign-in fails
     */
    MongoClientSettings clientSettings()
    {
        int timeout = settings.timeout();
        MongoClientSettings.Builder builder = MongoClientSettings.builder().applyConnectionString(new ConnectionString(settings.uri()))
                .credential(credential())
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(timeout, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS));
        SSLContext context = tls();
        if (context != null) {
            builder.applyToSslSettings(tls -> tls.context(context));
        }
        return builder.build();
    }

    private @Nullable SSLContext tls()
    {
        String ca = settings.caFile();
        String certificateKey = settings.certificateKeyFile();
        if (ca == null && certificateKey == null) {
            return null;
        }
        try {
            return MongoDbTls.context(ca == null ? null : Path.of(ca), certificateKey == null ? null : Path.of(certificateKey),
                    settings.certificateKeyPassword());
        }
        catch (IOException | GeneralSecurityException unusable) {
            throw new LookupException(LookupException.Reason.FAILED, "the TLS files cannot be used: " + unusable.getMessage(), unusable);
        }
    }

    /**
     * Builds the credential of the mechanism the settings name.
     *
     * @return it
     * @throws LookupException when the Kerberos sign-in fails
     */
    MongoCredential credential()
    {
        String user = settings.user();
        return switch (settings.mechanism()) {
            case MongoDbSettings.X509 -> user == null ? MongoCredential.createMongoX509Credential() : MongoCredential.createMongoX509Credential(user);
            case MongoDbSettings.PLAIN -> MongoCredential.createPlainCredential(required(user), MongoDbSettings.EXTERNAL, password());
            case MongoDbSettings.GSSAPI -> kerberos(required(user));
            case MongoDbSettings.SCRAM_SHA_1 -> MongoCredential.createScramSha1Credential(required(user), settings.source(), password());
            default -> MongoCredential.createScramSha256Credential(required(user), settings.source(), password());
        };
    }

    private MongoCredential kerberos(String principal)
    {
        try {
            return MongoCredential.createGSSAPICredential(principal)
                    .withMechanismProperty(MongoCredential.JAVA_SUBJECT_KEY, KerberosLogin.login(principal, settings.keytab(), settings.password(), settings.kdcs()))
                    .withMechanismProperty(MongoCredential.SERVICE_NAME_KEY, settings.serviceName());
        }
        catch (LoginException refused) {
            throw new LookupException(LookupException.Reason.AUTHENTICATION_FAILED, "Kerberos refused the sign-in of " + principal + ": " + refused.getMessage(),
                    refused);
        }
        catch (IOException unusable) {
            throw new LookupException(LookupException.Reason.FAILED, "the Kerberos sign-in cannot start: " + unusable.getMessage(), unusable);
        }
    }

    private static String required(@Nullable String user)
    {
        if (user == null) {
            throw new IllegalStateException("the settings checked the user");
        }
        return user;
    }

    private char[] password()
    {
        return requireNonNull(settings.password(), "the settings checked the password").toCharArray();
    }
}
