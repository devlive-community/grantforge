// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * The deployment through the official driver, which follows the replica set or the {@code mongos} list of the connection
 * string on its own and keeps its own pool of connections.
 */
final class DriverMongoDb
        implements MongoDb
{
    private final MongoClient client;

    DriverMongoDb(AgentConfig.Connection settings)
    {
        this(MongoClients.create(settings(settings)));
    }

    DriverMongoDb(MongoClient client)
    {
        this.client = client;
    }

    /**
     * Builds the driver's settings.
     *
     * @param settings the connection settings
     * @return the driver's settings
     * @throws MongoFailure when a TLS file or the Kerberos sign-in fails
     */
    static MongoClientSettings settings(AgentConfig.Connection settings)
    {
        int timeout = (int) settings.timeout().toMillis();
        MongoClientSettings.Builder builder = MongoClientSettings.builder().applyConnectionString(new ConnectionString(settings.uri()))
                .credential(credential(settings))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(timeout, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS));
        SSLContext context = tls(settings);
        if (context != null) {
            builder.applyToSslSettings(tls -> tls.context(context));
        }
        return builder.build();
    }

    private static @Nullable SSLContext tls(AgentConfig.Connection settings)
    {
        String ca = settings.caFile();
        String certificateKey = settings.certificateKeyFile();
        if (ca == null && certificateKey == null) {
            return null;
        }
        try {
            return Tls.context(ca == null ? null : Path.of(ca), certificateKey == null ? null : Path.of(certificateKey), settings.certificateKeyPassword());
        }
        catch (IOException | GeneralSecurityException unusable) {
            throw new MongoFailure("the TLS files cannot be used: " + unusable.getMessage(), false, unusable);
        }
    }

    /**
     * Builds the credential of the mechanism the settings name.
     *
     * @param settings the connection settings
     * @return it
     * @throws MongoFailure when the Kerberos sign-in fails
     */
    static MongoCredential credential(AgentConfig.Connection settings)
    {
        String user = settings.user();
        return switch (settings.mechanism()) {
            case AgentConfig.X509 -> user == null ? MongoCredential.createMongoX509Credential() : MongoCredential.createMongoX509Credential(user);
            case AgentConfig.PLAIN -> MongoCredential.createPlainCredential(required(user), AgentConfig.EXTERNAL, password(settings));
            case AgentConfig.GSSAPI -> kerberos(settings, required(user));
            case AgentConfig.SCRAM_SHA_1 -> MongoCredential.createScramSha1Credential(required(user), settings.source(), password(settings));
            default -> MongoCredential.createScramSha256Credential(required(user), settings.source(), password(settings));
        };
    }

    private static MongoCredential kerberos(AgentConfig.Connection settings, String principal)
    {
        try {
            return MongoCredential.createGSSAPICredential(principal)
                    .withMechanismProperty(MongoCredential.JAVA_SUBJECT_KEY, KerberosLogin.login(principal, settings.keytab(), settings.password()))
                    .withMechanismProperty(MongoCredential.SERVICE_NAME_KEY, settings.serviceName());
        }
        catch (LoginException | IOException refused) {
            throw new MongoFailure("the Kerberos sign-in of " + principal + " failed: " + refused.getMessage(), false, refused);
        }
    }

    private static String required(@Nullable String user)
    {
        if (user == null) {
            throw new IllegalStateException("the settings checked the user");
        }
        return user;
    }

    private static char[] password(AgentConfig.Connection settings)
    {
        return requireNonNull(settings.password(), "the settings checked the password").toCharArray();
    }

    @Override
    public Document command(String database, Document command)
    {
        try {
            return client.getDatabase(database).runCommand(command);
        }
        catch (MongoException failed) {
            throw failure(failed);
        }
    }

    @Override
    public List<String> databases()
    {
        try {
            return client.listDatabaseNames().into(new ArrayList<>());
        }
        catch (MongoException failed) {
            throw failure(failed);
        }
    }

    @Override
    public List<String> collections(String database)
    {
        try {
            return client.getDatabase(database).listCollectionNames().into(new ArrayList<>());
        }
        catch (MongoException failed) {
            throw failure(failed);
        }
    }

    @Override
    public List<Document> profile(String database, Instant after, Instant until, int limit)
    {
        try {
            return client.getDatabase(database).getCollection("system.profile")
                    .find(Filters.and(Filters.gt("ts", Date.from(after)), Filters.lte("ts", Date.from(until)))).sort(Sorts.ascending("ts")).limit(limit)
                    .into(new ArrayList<>());
        }
        catch (MongoException failed) {
            throw failure(failed);
        }
    }

    /**
     * Turns what the driver threw into the agent's failure.
     *
     * @param failed what the driver threw
     * @return the failure; unreachable when no server answered
     */
    static MongoFailure failure(MongoException failed)
    {
        boolean unreachable = failed instanceof MongoTimeoutException || failed instanceof MongoSocketException;
        return new MongoFailure(message(failed), unreachable, failed);
    }

    /** What the server said, without the driver's wrapping or the whole command, which a refusal repeats. */
    private static String message(MongoException failed)
    {
        Throwable cause = failed.getCause();
        String text;
        if (failed instanceof MongoSecurityException && cause instanceof MongoCommandException command) {
            text = "MongoDB refused the sign-in: " + command.getErrorMessage();
        }
        else if (failed instanceof MongoCommandException command) {
            text = command.getErrorMessage();
        }
        else {
            text = String.valueOf(failed.getMessage());
        }
        int repeated = text.indexOf(" to execute command");
        return repeated < 0 ? text : text.substring(0, repeated);
    }

    @Override
    public void close()
    {
        client.close();
    }
}
