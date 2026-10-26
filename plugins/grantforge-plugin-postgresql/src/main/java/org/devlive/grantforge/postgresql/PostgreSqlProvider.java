// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

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
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The PostgreSQL service type in PostgreSQL's own terms. Databases hold schemas, schemas hold tables, views and the
 * like, and tables hold columns; reading, writing and updating are decided by column, as PostgreSQL grants
 * {@code SELECT}, {@code INSERT} and {@code UPDATE} on columns, deleting, truncating and triggers by table, creating
 * objects by schema, and creating schemas and temporary tables by database. Names compare case-sensitively, as
 * PostgreSQL compares the names it stores. Row filters of tables are PostgreSQL expressions, as its row security
 * policies take them; PostgreSQL has no column masking, so the service type offers none.
 */
public final class PostgreSqlProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "postgresql";
    static final String DATABASE = "database";
    static final String SCHEMA = "schema";
    static final String TABLE = "table";
    static final String COLUMN = "column";

    static final String SELECT = "select";
    static final String INSERT = "insert";
    static final String UPDATE = "update";
    static final String DELETE = "delete";
    static final String TRUNCATE = "truncate";
    static final String TRIGGER = "trigger";
    static final String CREATE = "create";
    static final String CREATE_SCHEMA = "create_schema";
    static final String TEMPORARY = "temporary";

    private final Function<PostgreSqlSettings, PostgreSqlClient> clients;

    /** Creates the provider the plugin host loads. */
    public PostgreSqlProvider()
    {
        this(PostgreSqlClient::new);
    }

    PostgreSqlProvider(Function<PostgreSqlSettings, PostgreSqlClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("PostgreSQL").description("Databases, schemas, tables and columns of PostgreSQL servers")
                .resources(ResourceDefinition.builder(DATABASE).label("Database").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(CREATE_SCHEMA, TEMPORARY).build(),
                        ResourceDefinition.builder(SCHEMA).label("Schema").parent(DATABASE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(CREATE).build(),
                        ResourceDefinition.builder(TABLE).label("Table").parent(SCHEMA).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(DELETE, TRUNCATE, TRIGGER).build(),
                        ResourceDefinition.builder(COLUMN).label("Column").parent(TABLE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(SELECT, INSERT, UPDATE).build())
                .accessTypes(AccessTypeDefinition.of(SELECT, "Select"), AccessTypeDefinition.of(INSERT, "Insert"),
                        AccessTypeDefinition.of(UPDATE, "Update"), AccessTypeDefinition.of(DELETE, "Delete"),
                        AccessTypeDefinition.of(TRUNCATE, "Truncate"), AccessTypeDefinition.of(TRIGGER, "Create triggers"),
                        AccessTypeDefinition.of(CREATE, "Create tables, views and other objects"),
                        AccessTypeDefinition.of(CREATE_SCHEMA, "Create schemas"), AccessTypeDefinition.of(TEMPORARY, "Create temporary tables"))
                .rowFilter(new RowFilterDefinition(Set.of(TABLE)))
                .configFields(
                        ConfigField.builder(PostgreSqlSettings.HOSTS).label("PostgreSQL servers").type(ConfigFieldType.STRING).mandatory()
                                .description("A server as host:port, such as postgres1.example.com:5432; several separated by commas").build(),
                        ConfigField.builder(PostgreSqlSettings.DATABASE).label("Database").type(ConfigFieldType.STRING)
                                .defaultValue(PostgreSqlSettings.DEFAULT_DATABASE).description("The database the lookup user signs in to first,"
                                        + " to list the databases").build(),
                        ConfigField.builder(PostgreSqlSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(PostgreSqlSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(PostgreSqlSettings.PASSWORD_SIGN_IN)
                                .description("password, or certificate (a TLS client certificate that PostgreSQL's cert authentication maps to"
                                        + " the user)").build(),
                        ConfigField.builder(PostgreSqlSettings.USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("The lookup user; it needs to connect to the databases it lists names of").build(),
                        ConfigField.builder(PostgreSqlSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("password: the lookup user's password").build(),
                        ConfigField.builder(PostgreSqlSettings.SSL_MODE).label("TLS mode").type(ConfigFieldType.ENUM)
                                .options(PostgreSqlSettings.SSL_MODES.toArray(new String[0])).defaultValue(PostgreSqlSettings.DEFAULT_SSL_MODE)
                                .description("As libpq's sslmode: verify-full checks the server's certificate and name, verify-ca its"
                                        + " certificate").build(),
                        ConfigField.builder(PostgreSqlSettings.ROOT_CERTIFICATE).label("CA certificate").type(ConfigFieldType.STRING)
                                .description("verify-ca and verify-full: path on the GrantForge server of the PEM CA certificate the servers are"
                                        + " checked against; empty uses the JVM's").build(),
                        ConfigField.builder(PostgreSqlSettings.CLIENT_CERTIFICATE).label("Client certificate").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the PEM client certificate").build(),
                        ConfigField.builder(PostgreSqlSettings.CLIENT_KEY).label("Client key").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the client certificate's PKCS #8 private key, PEM"
                                        + " or DER").build(),
                        ConfigField.builder(PostgreSqlSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(PostgreSqlSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return PostgreSqlSettings.problems(config);
    }

    /** Signs in and describes the server: its release, whose user signed in and how many databases it has. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            PostgreSqlSettings settings = PostgreSqlSettings.of(config);
            return clients.apply(settings).run(settings.database(), connection -> {
                try (PreparedStatement statement = connection.prepareStatement("SELECT current_setting('server_version') AS version,"
                        + " current_user AS signed_in, (SELECT count(*) FROM pg_database WHERE datallowconn AND NOT datistemplate) AS databases");
                     ResultSet row = statement.executeQuery()) {
                    row.next();
                    long databases = row.getLong("databases");
                    return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "PostgreSQL " + version(row.getString("version"))
                            + ", signed in as " + row.getString("signed_in") + ", " + databases + (databases == 1 ? " database" : " databases"));
                }
            });
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /** The release of a server_version, without what packagers add after it, such as {@code 17.11 (Debian 17.11-1)}. */
    static String version(String serverVersion)
    {
        int space = serverVersion.indexOf(' ');
        return space < 0 ? serverVersion : serverVersion.substring(0, space);
    }

    /** Lists the databases, schemas, tables or columns that start with what was typed, within those the policy names. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        PostgreSqlSettings settings;
        PostgreSqlLookup lookup;
        try {
            settings = PostgreSqlSettings.of(request.config());
            lookup = PostgreSqlLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return lookup.run(clients.apply(settings), settings.database());
    }
}
