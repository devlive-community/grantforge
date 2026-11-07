// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.MongoException;
import org.bson.Document;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.List;
import java.util.function.Function;

/**
 * The MongoDB service type in MongoDB's own terms. Databases hold collections, and views count as collections, as MongoDB
 * grants them alike. A collection decides reading, change streams, writing by kind, its indexes, changing its options,
 * skipping document validation and dropping it; a database decides creating collections and dropping the database; the
 * deployment as a whole decides listing every database and monitoring. Names compare case-sensitively, as MongoDB
 * compares them. MongoDB filters no rows and masks no fields by role on its own, so the service type offers neither; a
 * policy on a view gives a restricted reading of a collection instead.
 */
public final class MongoDbProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "mongodb";
    static final String DEPLOYMENT = "deployment";
    static final String DATABASE = "database";
    static final String COLLECTION = "collection";

    /** What a sharded cluster is called in policies: it has no name of its own. */
    static final String SHARDED = "sharded";
    /** What a single server is called in policies. */
    static final String STANDALONE = "standalone";

    static final String READ = "read";
    static final String CHANGE_STREAM = "change_stream";
    static final String INSERT = "insert";
    static final String UPDATE = "update";
    static final String REMOVE = "remove";
    static final String CREATE_INDEX = "create_index";
    static final String DROP_INDEX = "drop_index";
    static final String ALTER = "alter";
    static final String BYPASS_VALIDATION = "bypass_validation";
    static final String DROP = "drop";
    static final String CREATE = "create";
    static final String DROP_DATABASE = "drop_database";
    static final String LIST_DATABASES = "list_databases";
    static final String MONITOR = "monitor";

    private final Function<MongoDbSettings, MongoDbClient> clients;

    /** Creates the provider the plugin host loads. */
    public MongoDbProvider()
    {
        this(MongoDbClient::new);
    }

    MongoDbProvider(Function<MongoDbSettings, MongoDbClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("MongoDB").description("Databases and collections of MongoDB replica sets and sharded clusters")
                .resources(ResourceDefinition.builder(DEPLOYMENT).label("Deployment").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(LIST_DATABASES, MONITOR).build(),
                        ResourceDefinition.builder(DATABASE).label("Database").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(CREATE, DROP_DATABASE).build(),
                        ResourceDefinition.builder(COLLECTION).label("Collection").parent(DATABASE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true)
                                .accessTypes(READ, CHANGE_STREAM, INSERT, UPDATE, REMOVE, CREATE_INDEX, DROP_INDEX, ALTER, BYPASS_VALIDATION, DROP).build())
                .accessTypes(AccessTypeDefinition.of(READ, "Read"), AccessTypeDefinition.of(CHANGE_STREAM, "Open change streams"),
                        AccessTypeDefinition.of(INSERT, "Insert"), AccessTypeDefinition.of(UPDATE, "Update"), AccessTypeDefinition.of(REMOVE, "Remove"),
                        AccessTypeDefinition.of(CREATE_INDEX, "Create indexes"), AccessTypeDefinition.of(DROP_INDEX, "Drop indexes"),
                        AccessTypeDefinition.of(ALTER, "Change options and validate"), AccessTypeDefinition.of(BYPASS_VALIDATION, "Skip document validation"),
                        AccessTypeDefinition.of(DROP, "Drop"), AccessTypeDefinition.of(CREATE, "Create collections and views"),
                        AccessTypeDefinition.of(DROP_DATABASE, "Drop database"), AccessTypeDefinition.of(LIST_DATABASES, "List every database"),
                        AccessTypeDefinition.of(MONITOR, "Monitor the deployment"))
                .configFields(
                        ConfigField.builder(MongoDbSettings.URI).label("Connection string").type(ConfigFieldType.STRING).mandatory()
                                .description("Such as mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0 or mongodb+srv://cluster.example.com/;"
                                        + " options go here, credentials do not").build(),
                        ConfigField.builder(MongoDbSettings.MECHANISM).label("Sign-in mechanism").type(ConfigFieldType.ENUM)
                                .options(MongoDbSettings.MECHANISMS.toArray(new String[0])).defaultValue(MongoDbSettings.SCRAM_SHA_256)
                                .description("SCRAM-SHA-256 or SCRAM-SHA-1 for users with a password, MONGODB-X509 for a client certificate, PLAIN for LDAP,"
                                        + " GSSAPI for Kerberos").build(),
                        ConfigField.builder(MongoDbSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("The lookup user; it sees the databases and collections it has any privilege on. With GSSAPI the principal;"
                                        + " with MONGODB-X509 empty takes it from the certificate").build(),
                        ConfigField.builder(MongoDbSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The lookup user's password").build(),
                        ConfigField.builder(MongoDbSettings.SOURCE).label("Authentication database").type(ConfigFieldType.STRING)
                                .defaultValue(MongoDbSettings.DEFAULT_SOURCE).description("SCRAM: the database the user is defined in").build(),
                        ConfigField.builder(MongoDbSettings.CA_FILE).label("CA file").type(ConfigFieldType.STRING)
                                .description("With tls=true: path on the GrantForge server of the PEM CA certificates the servers are checked against;"
                                        + " empty uses the JVM's").build(),
                        ConfigField.builder(MongoDbSettings.CERTIFICATE_KEY_FILE).label("Certificate and key file").type(ConfigFieldType.STRING)
                                .description("With tls=true: path on the GrantForge server of one PEM file with the client certificate and its key,"
                                        + " as mongosh takes it").build(),
                        ConfigField.builder(MongoDbSettings.CERTIFICATE_KEY_PASSWORD).label("Key password").type(ConfigFieldType.SECRET)
                                .description("The password of an encrypted key").build(),
                        ConfigField.builder(MongoDbSettings.KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("GSSAPI: path on the GrantForge server of the principal's keytab; empty uses the password").build(),
                        ConfigField.builder(MongoDbSettings.KDC).label("KDC").type(ConfigFieldType.STRING)
                                .description("GSSAPI: the realm's KDCs separated by commas; empty uses the server's krb5.conf").build(),
                        ConfigField.builder(MongoDbSettings.SERVICE_NAME).label("Kerberos service name").type(ConfigFieldType.STRING)
                                .defaultValue(MongoDbSettings.DEFAULT_SERVICE_NAME).description("GSSAPI: the service name of the servers' principal").build(),
                        ConfigField.builder(MongoDbSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(MongoDbSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return MongoDbSettings.problems(config);
    }

    /** Signs in and describes the deployment: its release and kind, whose user signed in, and how many databases it sees. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        MongoDbSettings settings;
        try {
            settings = MongoDbSettings.of(config);
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
        try {
            return clients.apply(settings).run(client -> {
                var admin = client.getDatabase("admin");
                String version = admin.runCommand(new Document("buildInfo", 1)).getString("version");
                String deployment = describe(MongoDbLookup.deployment(admin.runCommand(new Document("hello", 1))));
                String user = signedIn(admin.runCommand(new Document("connectionStatus", 1)));
                int databases = MongoDbLookup.databases(client).size();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "MongoDB " + version + ", " + deployment + ", signed in as " + user + ", "
                        + databases + (databases == 1 ? " database" : " databases"));
            });
        }
        catch (MongoException failed) {
            return ConnectionResult.failed(String.valueOf(MongoDbLookup.failure(failed).getMessage()));
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
    }

    private static String describe(String deployment)
    {
        return switch (deployment) {
            case SHARDED -> "a sharded cluster";
            case STANDALONE -> "a single server";
            default -> "replica set " + deployment;
        };
    }

    /** The user of a {@code connectionStatus} answer, as {@code user@database}. */
    // A Document is what the driver answers with, not a choice among maps.
    @SuppressWarnings("PMD.LooseCoupling")
    private static String signedIn(Document status)
    {
        Document info = status.get("authInfo", Document.class);
        List<Document> users = info == null ? null : info.getList("authenticatedUsers", Document.class);
        if (users == null || users.isEmpty()) {
            return "nobody";
        }
        return users.get(0).getString("user") + "@" + users.get(0).getString("db");
    }

    /** Lists the deployment, the databases or the collections that start with what was typed. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        MongoDbSettings settings;
        MongoDbLookup lookup;
        try {
            settings = MongoDbSettings.of(request.config());
            lookup = MongoDbLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return lookup.run(clients.apply(settings));
    }
}
