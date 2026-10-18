// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.hbase.ClusterMetrics;
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
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

/**
 * The Apache HBase service type in HBase's own terms. Its levels are the scopes of HBase's permissions: namespaces, tables
 * within them, column families and columns, and the global scope of operations on the whole cluster; its access types are
 * HBase's read, write, create and admin. HBase's exec, for coprocessor endpoints, is left out: the observer hook that
 * would check it differs between HBase 2 and 3. A namespace is a level of its own, so a namespace's owners may be given
 * its tables without naming them.
 */
public final class HBaseProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "hbase";
    static final String GLOBAL = "global";
    static final String NAMESPACE = "namespace";
    static final String TABLE = "table";
    static final String COLUMN_FAMILY = "column_family";
    static final String COLUMN = "column";
    static final String READ = "read";
    static final String WRITE = "write";
    static final String CREATE = "create";
    static final String ADMIN = "admin";

    /** The one value of the global scope. */
    static final String GLOBAL_VALUE = "*";

    private final Function<HBaseSettings, HBaseClient> clients;

    /** Creates the provider the plugin host loads. */
    public HBaseProvider()
    {
        this(HBaseClient::new);
    }

    HBaseProvider(Function<HBaseSettings, HBaseClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache HBase").description("Namespaces, tables, column families and columns of HBase")
                .resources(ResourceDefinition.builder(GLOBAL).label("Global").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(false)
                                .lookupSupported(true).accessTypes(ADMIN).build(),
                        level(NAMESPACE, "Namespace", null).validLeaf(true).accessTypes(READ, WRITE, CREATE, ADMIN).build(),
                        level(TABLE, "Table", NAMESPACE).validLeaf(true).accessTypes(READ, WRITE, CREATE, ADMIN).build(),
                        level(COLUMN_FAMILY, "Column family", TABLE).validLeaf(true).accessTypes(READ, WRITE).build(),
                        level(COLUMN, "Column", COLUMN_FAMILY).lookupSupported(false).accessTypes(READ, WRITE).build())
                .accessTypes(AccessTypeDefinition.of(READ, "Read"), AccessTypeDefinition.of(WRITE, "Write"), AccessTypeDefinition.of(CREATE, "Create"),
                        AccessTypeDefinition.of(ADMIN, "Admin"))
                .configFields(
                        ConfigField.builder(HBaseSettings.QUORUM).label("ZooKeeper quorum").type(ConfigFieldType.STRING)
                                .description("ZooKeeper hosts, host or host:port separated by commas, such as zk1,zk2,zk3:2181").build(),
                        ConfigField.builder(HBaseSettings.ZOOKEEPER_PORT).label("ZooKeeper port").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(HBaseSettings.DEFAULT_ZOOKEEPER_PORT))
                                .description("The port of quorum hosts that name none").build(),
                        ConfigField.builder(HBaseSettings.ZNODE).label("Parent znode").type(ConfigFieldType.STRING)
                                .defaultValue(HBaseSettings.DEFAULT_ZNODE).description("The cluster's root in ZooKeeper").build(),
                        ConfigField.builder(HBaseSettings.BOOTSTRAP).label("Bootstrap servers").type(ConfigFieldType.STRING)
                                .description("Instead of ZooKeeper, from HBase 2.5 on: masters or region servers, host:port separated by commas")
                                .build(),
                        ConfigField.builder(HBaseSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(HBaseSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(HBaseSettings.SIMPLE)
                                .description("simple: a user name; kerberos: a principal with a keytab or password").build(),
                        ConfigField.builder(HBaseSettings.USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("Who looks up namespaces and tables; with Kerberos the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(HBaseSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("Kerberos: the principal's password, unless a keytab is given").build(),
                        ConfigField.builder(HBaseSettings.KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Kerberos: path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(HBaseSettings.KDC).label("KDCs").type(ConfigFieldType.STRING)
                                .description("Kerberos: KDCs of the principal's realm, host[:port] separated by commas; empty uses the server's"
                                        + " krb5.conf").build(),
                        ConfigField.builder(HBaseSettings.MASTER_PRINCIPAL).label("Master principal").type(ConfigFieldType.STRING)
                                .description("Kerberos: the masters' principal, default hbase/_HOST@ the lookup principal's realm").build(),
                        ConfigField.builder(HBaseSettings.REGION_SERVER_PRINCIPAL).label("Region server principal").type(ConfigFieldType.STRING)
                                .description("Kerberos: the region servers' principal, default the masters'").build(),
                        ConfigField.builder(HBaseSettings.PROTECTION).label("RPC protection").type(ConfigFieldType.ENUM)
                                .options(HBaseSettings.PROTECTIONS.toArray(new String[0])).defaultValue("authentication")
                                .description("Kerberos: authentication, integrity or privacy, as the cluster's hbase.rpc.protection").build(),
                        ConfigField.builder(HBaseSettings.TIMEOUT).label("RPC timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(HBaseSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    private static ResourceDefinition.Builder level(String name, String label, @Nullable String parent)
    {
        ResourceDefinition.Builder level = ResourceDefinition.builder(name).label(label).matcher(MatcherType.WILDCARD).caseSensitive(true)
                .excludesSupported(true).lookupSupported(true);
        return parent == null ? level : level.parent(parent);
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return HBaseSettings.problems(config);
    }

    /** Signs in and describes the cluster: its id, its release and how many region servers are live. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return clients.apply(HBaseSettings.of(config)).run(admin -> {
                ClusterMetrics metrics = admin.getClusterMetrics(EnumSet.of(ClusterMetrics.Option.CLUSTER_ID, ClusterMetrics.Option.HBASE_VERSION,
                        ClusterMetrics.Option.LIVE_SERVERS));
                int servers = metrics.getLiveServerMetrics().size();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "cluster " + metrics.getClusterId() + ", HBase "
                        + metrics.getHBaseVersion() + ", " + servers + (servers == 1 ? " region server" : " region servers"));
            });
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /**
     * Lists the namespaces, tables or column families whose names start with what was typed, within the namespaces and
     * tables the policy names already. The global scope has its one value; columns hold data and have nothing to list.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        HBaseSettings settings;
        try {
            settings = HBaseSettings.of(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        if (GLOBAL.equals(request.resource())) {
            return GLOBAL_VALUE.startsWith(request.userInput().strip()) ? List.of(GLOBAL_VALUE) : List.of();
        }
        HBaseLookup lookup;
        try {
            lookup = HBaseLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return clients.apply(settings).run(lookup::run);
    }
}
