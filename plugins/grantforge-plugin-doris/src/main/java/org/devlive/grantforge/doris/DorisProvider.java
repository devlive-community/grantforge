// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The Apache Doris service type in Doris's own terms. Catalogs hold databases, databases hold tables and tables hold
 * columns; reading is decided by column, as Doris grants {@code SELECT_PRIV} on columns, and loading, altering,
 * dropping and showing views by table. A database decides creating tables and views in it and dropping it, and a
 * catalog creating databases in it. Workload groups decide who may run queries in them. Names of catalogs, databases
 * and tables compare case-sensitively, as Doris compares them by default; column names do not. Row filters of tables
 * are Doris predicates, as its row policies take them; Doris masks no values on its own, so the service type offers no
 * masking.
 */
public final class DorisProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "doris";
    static final String CATALOG = "catalog";
    static final String DATABASE = "database";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String WORKLOAD_GROUP = "workload_group";

    static final String SELECT = "select";
    static final String LOAD = "load";
    static final String ALTER = "alter";
    static final String DROP = "drop";
    static final String SHOW_VIEW = "show_view";
    static final String CREATE = "create";
    static final String DROP_DATABASE = "drop_database";
    static final String CREATE_DATABASE = "create_database";
    static final String USAGE = "usage";

    private final Function<DorisSettings, DorisClient> clients;

    /** Creates the provider the plugin host loads. */
    public DorisProvider()
    {
        this(DorisClient::new);
    }

    DorisProvider(Function<DorisSettings, DorisClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache Doris").description("Catalogs, databases, tables and columns of Apache Doris"
                        + " clusters, and their workload groups")
                .resources(ResourceDefinition.builder(CATALOG).label("Catalog").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(CREATE_DATABASE).build(),
                        ResourceDefinition.builder(DATABASE).label("Database").parent(CATALOG).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(CREATE, DROP_DATABASE).build(),
                        ResourceDefinition.builder(TABLE).label("Table").parent(DATABASE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(LOAD, ALTER, DROP, SHOW_VIEW).build(),
                        ResourceDefinition.builder(COLUMN).label("Column").parent(TABLE).matcher(MatcherType.WILDCARD).caseSensitive(false)
                                .excludesSupported(true).lookupSupported(true).accessTypes(SELECT).build(),
                        ResourceDefinition.builder(WORKLOAD_GROUP).label("Workload group").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(USAGE).build())
                .accessTypes(AccessTypeDefinition.of(SELECT, "Select"), AccessTypeDefinition.of(LOAD, "Load, insert, update and delete"),
                        AccessTypeDefinition.of(ALTER, "Alter"), AccessTypeDefinition.of(DROP, "Drop"),
                        AccessTypeDefinition.of(SHOW_VIEW, "Show view definitions"),
                        AccessTypeDefinition.of(CREATE, "Create tables and views"), AccessTypeDefinition.of(DROP_DATABASE, "Drop database"),
                        AccessTypeDefinition.of(CREATE_DATABASE, "Create databases"),
                        AccessTypeDefinition.of(USAGE, "Run queries in the workload group"))
                .rowFilter(new RowFilterDefinition(Set.of(TABLE)))
                .configFields(
                        ConfigField.builder(DorisSettings.HOSTS).label("Frontends").type(ConfigFieldType.STRING).mandatory()
                                .description("A frontend's MySQL protocol address, such as fe1.example.com:9030; several separated by commas").build(),
                        ConfigField.builder(DorisSettings.USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("The lookup user; it sees the catalogs, databases, tables and columns it has any privilege on").build(),
                        ConfigField.builder(DorisSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The lookup user's password").build(),
                        ConfigField.builder(DorisSettings.SSL_MODE).label("TLS mode").type(ConfigFieldType.ENUM)
                                .options(DorisSettings.SSL_MODES.toArray(new String[0])).defaultValue(DorisSettings.DISABLE)
                                .description("disable, trust (encrypt without checking), verify-ca or verify-full (also check the frontend's name)")
                                .build(),
                        ConfigField.builder(DorisSettings.ROOT_CERTIFICATE).label("CA certificate").type(ConfigFieldType.STRING)
                                .description("verify-ca and verify-full: path on the GrantForge server of the PEM CA certificate the frontends are"
                                        + " checked against; empty uses the JVM's").build(),
                        ConfigField.builder(DorisSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(DorisSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return DorisSettings.problems(config);
    }

    /** Signs in and describes the cluster: its release, whose user signed in, and how many catalogs and databases it sees. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        DorisSettings settings;
        try {
            settings = DorisSettings.of(config);
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
        try {
            return clients.apply(settings).run(connection -> {
                // SHOW statements only, which the frontend answers itself, so the test works while no backend is up.
                try (Statement statement = connection.createStatement()) {
                    String version = null;
                    try (ResultSet row = statement.executeQuery("SHOW VARIABLES LIKE 'version_comment'")) {
                        if (row.next()) {
                            version = row.getString(2);
                        }
                    }
                    String user = "an unknown user";
                    try (ResultSet row = statement.executeQuery("SHOW GRANTS")) {
                        if (row.next()) {
                            user = row.getString(1);
                        }
                    }
                    version = DorisLookup.release(version);
                    int catalogs = DorisLookup.catalogs(statement).size();
                    int databases;
                    try (ResultSet rows = statement.executeQuery("SHOW DATABASES")) {
                        databases = DorisLookup.count(rows, DorisLookup.SYSTEM_DATABASES);
                    }
                    return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "Apache Doris " + version + ", signed in as " + user + ", "
                            + catalogs + (catalogs == 1 ? " catalog, " : " catalogs, ") + databases + (databases == 1 ? " database" : " databases")
                            + " in the internal catalog");
                }
            });
        }
        catch (SQLException failed) {
            return ConnectionResult.failed(String.valueOf(DorisLookup.failure(failed).getMessage()));
        }
    }

    /** Lists the catalogs, databases, tables, columns or workload groups that start with what was typed. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        DorisSettings settings;
        DorisLookup lookup;
        try {
            settings = DorisSettings.of(request.config());
            lookup = DorisLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return lookup.run(clients.apply(settings));
    }
}
