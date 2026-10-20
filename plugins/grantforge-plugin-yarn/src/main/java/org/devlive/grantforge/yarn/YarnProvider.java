// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConditionDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.List;
import java.util.function.Function;

/**
 * The Apache Hadoop YARN service type in YARN's own terms. Queues are named by their path from the root, such as
 * {@code root.sales.etl}, and as in YARN a permission on a queue covers the queues below it; below a queue, applications
 * are named, so that a policy can say which applications may be submitted where. The cluster level is YARN's
 * administration, which {@code yarn.admin.acl} grants otherwise. Policy items may hold for some client addresses only.
 */
public final class YarnProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "yarn";
    static final String QUEUE = "queue";
    static final String APPLICATION = "application";
    static final String CLUSTER = "cluster";
    static final String SUBMIT_APP = "submit_app";
    static final String ADMIN_QUEUE = "admin_queue";
    static final String ADMIN = "admin";
    static final String IP_RANGE = "ip-range";

    /** The one value of the cluster level. */
    static final String CLUSTER_VALUE = "*";

    private final Function<YarnSettings, YarnClusterClient> clients;

    /** Creates the provider the plugin host loads. */
    public YarnProvider()
    {
        this(YarnClusterClient::new);
    }

    YarnProvider(Function<YarnSettings, YarnClusterClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache Hadoop YARN").description("Queues of YARN, the applications submitted to them and"
                        + " the cluster's administration")
                .resources(ResourceDefinition.builder(QUEUE).label("Queue").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                                .lookupSupported(true).validLeaf(true).accessTypes(SUBMIT_APP, ADMIN_QUEUE).build(),
                        ResourceDefinition.builder(APPLICATION).label("Application name").parent(QUEUE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(SUBMIT_APP).build(),
                        ResourceDefinition.builder(CLUSTER).label("Cluster").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(false)
                                .lookupSupported(true).accessTypes(ADMIN).build())
                .accessTypes(AccessTypeDefinition.of(SUBMIT_APP, "Submit applications"),
                        AccessTypeDefinition.of(ADMIN_QUEUE, "Administer queue", SUBMIT_APP), AccessTypeDefinition.of(ADMIN, "Administer cluster"))
                .conditions(ConditionDefinition.of(IP_RANGE, "Client addresses", IP_RANGE))
                .configFields(
                        ConfigField.builder(YarnSettings.RESOURCE_MANAGER).label("ResourceManagers").type(ConfigFieldType.STRING).mandatory()
                                .description("host or host:port, default port 8032; several, separated by commas, for a highly available cluster")
                                .build(),
                        ConfigField.builder(YarnSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(YarnSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(YarnSettings.SIMPLE)
                                .description("simple: a user name; kerberos: a principal with a keytab or password").build(),
                        ConfigField.builder(YarnSettings.USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("Who looks up queues and applications; with Kerberos the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(YarnSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("Kerberos: the principal's password, unless a keytab is given").build(),
                        ConfigField.builder(YarnSettings.KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Kerberos: path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(YarnSettings.KDC).label("KDCs").type(ConfigFieldType.STRING)
                                .description("Kerberos: KDCs of the principal's realm, host[:port] separated by commas; empty uses the server's"
                                        + " krb5.conf").build(),
                        ConfigField.builder(YarnSettings.PRINCIPAL).label("ResourceManager principal").type(ConfigFieldType.STRING)
                                .description("Kerberos: the ResourceManagers' principal, default yarn/_HOST@ the lookup principal's realm").build(),
                        ConfigField.builder(YarnSettings.PROTECTION).label("RPC protection").type(ConfigFieldType.ENUM)
                                .options(YarnSettings.PROTECTIONS.toArray(new String[0])).defaultValue("authentication")
                                .description("Kerberos: authentication, integrity or privacy, as the cluster's hadoop.rpc.protection").build(),
                        ConfigField.builder(YarnSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(YarnSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return YarnSettings.problems(config);
    }

    /** Signs in and describes the cluster: how many NodeManagers it has and how many queues. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return clients.apply(YarnSettings.of(config)).run(client -> {
                YarnClusterMetrics metrics = client.getYarnClusterMetrics();
                int nodes = metrics.getNumNodeManagers();
                int queues = YarnLookup.paths(client.getRootQueueInfos()).size();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, nodes + (nodes == 1 ? " NodeManager, " : " NodeManagers, ") + queues
                        + (queues == 1 ? " queue" : " queues"));
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
     * Lists the queues, or the names of the applications in the queues the policy names, that start with what was typed.
     * The cluster level has its one value.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        YarnSettings settings;
        YarnLookup lookup;
        try {
            settings = YarnSettings.of(request.config());
            if (CLUSTER.equals(request.resource())) {
                return CLUSTER_VALUE.startsWith(request.userInput().strip()) ? List.of(CLUSTER_VALUE) : List.of();
            }
            lookup = YarnLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return clients.apply(settings).run(lookup::run);
    }
}
