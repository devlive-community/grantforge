// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import com.clickhouse.client.api.query.GenericRecord;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The ClickHouse service type in ClickHouse's own terms. Databases hold tables and tables hold columns; reading and
 * writing are decided by column, as ClickHouse grants {@code SELECT} and {@code INSERT} on columns, and the other
 * privileges of a table by table. A database also decides creating tables in it and dropping it, and the server level
 * creating databases, killing queries and the {@code SYSTEM} statements. Names compare case-sensitively, as ClickHouse
 * compares them. Row filters of tables are ClickHouse expressions, as its row policies take them; ClickHouse has no
 * column masking outside its cloud, so the service type offers none.
 */
public final class ClickHouseProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "clickhouse";
    static final String DATABASE = "database";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String SERVER = "server";

    static final String SELECT = "select";
    static final String INSERT = "insert";
    static final String ALTER = "alter";
    static final String DROP = "drop";
    static final String TRUNCATE = "truncate";
    static final String OPTIMIZE = "optimize";
    static final String CREATE = "create";
    static final String DROP_DATABASE = "drop_database";
    static final String CREATE_DATABASE = "create_database";
    static final String KILL_QUERY = "kill_query";
    static final String SYSTEM = "system";

    private final Function<ClickHouseSettings, ClickHouseNodeClient> clients;

    /** Creates the provider the plugin host loads. */
    public ClickHouseProvider()
    {
        this(ClickHouseNodeClient::new);
    }

    ClickHouseProvider(Function<ClickHouseSettings, ClickHouseNodeClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("ClickHouse").description("Databases, tables and columns of ClickHouse servers and"
                        + " the server as a whole")
                .resources(ResourceDefinition.builder(DATABASE).label("Database").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(CREATE, DROP_DATABASE).build(),
                        ResourceDefinition.builder(TABLE).label("Table").parent(DATABASE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(ALTER, DROP, TRUNCATE, OPTIMIZE).build(),
                        ResourceDefinition.builder(COLUMN).label("Column").parent(TABLE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(SELECT, INSERT).build(),
                        ResourceDefinition.builder(SERVER).label("Server").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(false)
                                .lookupSupported(true).accessTypes(CREATE_DATABASE, KILL_QUERY, SYSTEM).build())
                .accessTypes(AccessTypeDefinition.of(SELECT, "Select"), AccessTypeDefinition.of(INSERT, "Insert"),
                        AccessTypeDefinition.of(ALTER, "Alter"), AccessTypeDefinition.of(DROP, "Drop"),
                        AccessTypeDefinition.of(TRUNCATE, "Truncate"), AccessTypeDefinition.of(OPTIMIZE, "Optimize"),
                        AccessTypeDefinition.of(CREATE, "Create tables, views and dictionaries"),
                        AccessTypeDefinition.of(DROP_DATABASE, "Drop database"),
                        AccessTypeDefinition.of(CREATE_DATABASE, "Create databases"), AccessTypeDefinition.of(KILL_QUERY, "Kill queries"),
                        AccessTypeDefinition.of(SYSTEM, "SYSTEM statements"))
                .rowFilter(new RowFilterDefinition(Set.of(TABLE)))
                .configFields(
                        ConfigField.builder(ClickHouseSettings.URLS).label("ClickHouse URLs").type(ConfigFieldType.STRING).mandatory()
                                .description("A server's HTTP interface, such as https://clickhouse1.example.com:8443; several separated by commas")
                                .build(),
                        ConfigField.builder(ClickHouseSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(ClickHouseSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(ClickHouseSettings.PASSWORD_SIGN_IN)
                                .description("password, or certificate (a TLS client certificate that ClickHouse maps to the user)").build(),
                        ConfigField.builder(ClickHouseSettings.USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("The lookup user; it sees the databases, tables and columns it has any privilege on").build(),
                        ConfigField.builder(ClickHouseSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("password: the lookup user's password").build(),
                        ConfigField.builder(ClickHouseSettings.ROOT_CERTIFICATE).label("CA certificate").type(ConfigFieldType.STRING)
                                .description("https: path on the GrantForge server of the PEM CA certificate the servers are checked against;"
                                        + " empty uses the JVM's").build(),
                        ConfigField.builder(ClickHouseSettings.CLIENT_CERTIFICATE).label("Client certificate").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the PEM client certificate").build(),
                        ConfigField.builder(ClickHouseSettings.CLIENT_KEY).label("Client key").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the client certificate's PEM private key").build(),
                        ConfigField.builder(ClickHouseSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(ClickHouseSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return ClickHouseSettings.problems(config);
    }

    /** Signs in and describes the server: its release, whose user signed in and how many databases the user sees. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            List<GenericRecord> rows = clients.apply(ClickHouseSettings.of(config)).query("SELECT version() AS version, currentUser() AS user,"
                    + " (SELECT count() FROM system.databases WHERE name NOT IN {system:Array(String)}) AS databases",
                    Map.of("system", ClickHouseLookup.SYSTEM_DATABASES));
            GenericRecord row = rows.get(0);
            long databases = row.getLong("databases");
            return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "ClickHouse " + row.getString("version") + ", signed in as "
                    + row.getString("user") + ", " + databases + (databases == 1 ? " database" : " databases"));
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /** Lists the databases, tables or columns that start with what was typed, within those the policy names. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        ClickHouseSettings settings;
        ClickHouseLookup lookup;
        try {
            settings = ClickHouseSettings.of(request.config());
            lookup = ClickHouseLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return lookup.run(clients.apply(settings));
    }
}
