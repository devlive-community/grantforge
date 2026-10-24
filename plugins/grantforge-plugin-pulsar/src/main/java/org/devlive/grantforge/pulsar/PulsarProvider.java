// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

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
 * The Apache Pulsar service type in Pulsar's own terms. A topic is named by its tenant, its namespace and its own name,
 * and as with Pulsar's own grants a permission on a tenant or a namespace covers what is in it; below a topic,
 * subscriptions are named, so that a policy can say which subscriptions a role may consume through. The actions are
 * Pulsar's: produce, consume, functions, sources, sinks and packages, and the administration of topics, namespaces and
 * tenants; the cluster level is Pulsar's superuser access. Policy items may hold for some client addresses only.
 */
public final class PulsarProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "pulsar";
    static final String TENANT = "tenant";
    static final String NAMESPACE = "namespace";
    static final String TOPIC = "topic";
    static final String SUBSCRIPTION = "subscription";
    static final String CLUSTER = "cluster";

    static final String PRODUCE = "produce";
    static final String CONSUME = "consume";
    static final String FUNCTIONS = "functions";
    static final String SOURCES = "sources";
    static final String SINKS = "sinks";
    static final String PACKAGES = "packages";
    static final String ADMIN_TOPIC = "admin_topic";
    static final String ADMIN_NAMESPACE = "admin_namespace";
    static final String ADMIN_TENANT = "admin_tenant";
    static final String ADMIN = "admin";
    static final String IP_RANGE = "ip-range";

    private final Function<PulsarSettings, PulsarAdminClient> clients;

    /** Creates the provider the plugin host loads. */
    public PulsarProvider()
    {
        this(PulsarAdminClient::new);
    }

    PulsarProvider(Function<PulsarSettings, PulsarAdminClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache Pulsar").description("Tenants, namespaces, topics and subscriptions of Pulsar, and"
                        + " the clusters' administration")
                .resources(ResourceDefinition.builder(TENANT).label("Tenant").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                                .lookupSupported(true).validLeaf(true).accessTypes(PRODUCE, CONSUME, FUNCTIONS, SOURCES, SINKS, PACKAGES, ADMIN_TOPIC,
                                        ADMIN_NAMESPACE, ADMIN_TENANT).build(),
                        ResourceDefinition.builder(NAMESPACE).label("Namespace").parent(TENANT).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(PRODUCE, CONSUME, FUNCTIONS, SOURCES, SINKS,
                                        PACKAGES, ADMIN_TOPIC, ADMIN_NAMESPACE).build(),
                        ResourceDefinition.builder(TOPIC).label("Topic").parent(NAMESPACE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(PRODUCE, CONSUME, ADMIN_TOPIC).build(),
                        ResourceDefinition.builder(SUBSCRIPTION).label("Subscription").parent(TOPIC).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(CONSUME).build(),
                        ResourceDefinition.builder(CLUSTER).label("Cluster").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                                .lookupSupported(true).accessTypes(ADMIN).build())
                .accessTypes(AccessTypeDefinition.of(PRODUCE, "Produce"), AccessTypeDefinition.of(CONSUME, "Consume"),
                        AccessTypeDefinition.of(FUNCTIONS, "Functions"), AccessTypeDefinition.of(SOURCES, "Sources"), AccessTypeDefinition.of(SINKS, "Sinks"),
                        AccessTypeDefinition.of(PACKAGES, "Packages"), AccessTypeDefinition.of(ADMIN_TOPIC, "Administer topic", PRODUCE, CONSUME),
                        AccessTypeDefinition.of(ADMIN_NAMESPACE, "Administer namespace", ADMIN_TOPIC),
                        AccessTypeDefinition.of(ADMIN_TENANT, "Administer tenant", ADMIN_NAMESPACE, FUNCTIONS, SOURCES, SINKS, PACKAGES),
                        AccessTypeDefinition.of(ADMIN, "Administer cluster", ADMIN_TENANT))
                .conditions(ConditionDefinition.of(IP_RANGE, "Client addresses", IP_RANGE))
                .configFields(
                        ConfigField.builder(PulsarSettings.WEB_SERVICE_URL).label("Web service URL").type(ConfigFieldType.STRING).mandatory()
                                .description("http://host:port or https://host:port of a broker, default port 8080 or 8443; several brokers separated by"
                                        + " commas, such as http://broker1:8080,broker2:8080").build(),
                        ConfigField.builder(PulsarSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(PulsarSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(PulsarSettings.NONE)
                                .description("none, token, basic (user name and password), oauth2 (client credentials) or tls (client certificate)").build(),
                        ConfigField.builder(PulsarSettings.TOKEN_FIELD).label("Token").type(ConfigFieldType.SECRET)
                                .description("token: the lookup role's token").build(),
                        ConfigField.builder(PulsarSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("basic: the lookup role's user name").build(),
                        ConfigField.builder(PulsarSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("basic: the user's password").build(),
                        ConfigField.builder(PulsarSettings.ISSUER_URL).label("OAuth 2.0 issuer URL").type(ConfigFieldType.STRING)
                                .description("oauth2: the authorization server, such as https://auth.example.com/").build(),
                        ConfigField.builder(PulsarSettings.PRIVATE_KEY).label("OAuth 2.0 credentials file").type(ConfigFieldType.STRING)
                                .description("oauth2: path on the GrantForge server of the JSON file with client_id and client_secret").build(),
                        ConfigField.builder(PulsarSettings.AUDIENCE).label("OAuth 2.0 audience").type(ConfigFieldType.STRING)
                                .description("oauth2: the audience the brokers expect, if any").build(),
                        ConfigField.builder(PulsarSettings.SCOPE).label("OAuth 2.0 scope").type(ConfigFieldType.STRING)
                                .description("oauth2: the scope to ask for, if any").build(),
                        ConfigField.builder(PulsarSettings.CERTIFICATE).label("Client certificate").type(ConfigFieldType.STRING)
                                .description("tls: path on the GrantForge server of the client certificate, PEM").build(),
                        ConfigField.builder(PulsarSettings.KEY).label("Client key").type(ConfigFieldType.STRING)
                                .description("tls: path on the GrantForge server of the client certificate's private key, PEM").build(),
                        ConfigField.builder(PulsarSettings.TRUST_CERTS).label("Trusted certificates").type(ConfigFieldType.STRING)
                                .description("https: path on the GrantForge server of the CA certificates the brokers' certificates are checked"
                                        + " against, PEM; empty uses the JVM's").build(),
                        ConfigField.builder(PulsarSettings.HOSTNAME_VERIFICATION).label("Verify host names").type(ConfigFieldType.BOOLEAN)
                                .defaultValue("true").description("https: whether a broker's certificate must name its host").build(),
                        ConfigField.builder(PulsarSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(PulsarSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return PulsarSettings.problems(config);
    }

    /** Signs in and describes the cluster: its clusters and how many tenants it has. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return clients.apply(PulsarSettings.of(config)).run(admin -> {
                List<String> clusters = admin.clusters().getClusters();
                int tenants = admin.tenants().getTenants().size();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, (clusters.size() == 1 ? "cluster " : "clusters ")
                        + String.join(", ", clusters) + ", " + tenants + (tenants == 1 ? " tenant" : " tenants"));
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
     * Lists the clusters, tenants, or the namespaces, topics or subscriptions in what the policy names, that start with
     * what was typed.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        PulsarSettings settings;
        PulsarLookup lookup;
        try {
            settings = PulsarSettings.of(request.config());
            lookup = PulsarLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return clients.apply(settings).run(lookup::run);
    }
}
