// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.apache.solr.common.util.NamedList;
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
 * The Apache Solr service type in Solr's own terms. Collections are named as Solr names them, and below a collection its
 * request paths, such as {@code /select} or {@code /export}, so that a policy can say through which handlers a user may
 * query or update. The access types group Solr's own permission names; {@code admin_collection} administers the
 * collections a request names through the Collections API, so that a team may create, reload or back up its own
 * collections. The cluster level is the administration of the nodes, from health checks to security, and {@code admin}
 * on it grants everything. Policy items may hold for some client addresses only.
 */
public final class SolrProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "solr";
    static final String COLLECTION = "collection";
    static final String PATH = "path";
    static final String CLUSTER = "cluster";

    static final String QUERY = "query";
    static final String UPDATE = "update";
    static final String SCHEMA_READ = "schema_read";
    static final String SCHEMA_EDIT = "schema_edit";
    static final String CONFIG_READ = "config_read";
    static final String CONFIG_EDIT = "config_edit";
    static final String ADMIN_COLLECTION = "admin_collection";
    static final String HEALTH = "health";
    static final String METRICS = "metrics";
    static final String ADMIN_READ = "admin_read";
    static final String SECURITY = "security";
    static final String ADMIN = "admin";
    static final String IP_RANGE = "ip-range";

    /** The one value of the cluster level. */
    static final String CLUSTER_VALUE = "*";

    private final Function<SolrSettings, SolrNodeClient> clients;

    /** Creates the provider the plugin host loads. */
    public SolrProvider()
    {
        this(SolrNodeClient::new);
    }

    SolrProvider(Function<SolrSettings, SolrNodeClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache Solr").description("Collections of Solr, the request paths within them and the"
                        + " cluster's administration")
                .resources(ResourceDefinition.builder(COLLECTION).label("Collection").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).accessTypes(QUERY, UPDATE, SCHEMA_READ, SCHEMA_EDIT,
                                        CONFIG_READ, CONFIG_EDIT, ADMIN_COLLECTION).build(),
                        ResourceDefinition.builder(PATH).label("Request path").parent(COLLECTION).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(QUERY, UPDATE, SCHEMA_READ, SCHEMA_EDIT, CONFIG_READ,
                                        CONFIG_EDIT).build(),
                        ResourceDefinition.builder(CLUSTER).label("Cluster").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(false)
                                .lookupSupported(true).accessTypes(HEALTH, METRICS, ADMIN_READ, SECURITY, ADMIN).build())
                .accessTypes(AccessTypeDefinition.of(QUERY, "Query"), AccessTypeDefinition.of(UPDATE, "Update"),
                        AccessTypeDefinition.of(SCHEMA_READ, "Read schema"), AccessTypeDefinition.of(SCHEMA_EDIT, "Edit schema", SCHEMA_READ),
                        AccessTypeDefinition.of(CONFIG_READ, "Read config"), AccessTypeDefinition.of(CONFIG_EDIT, "Edit config", CONFIG_READ),
                        AccessTypeDefinition.of(ADMIN_COLLECTION, "Administer collection", QUERY, UPDATE, SCHEMA_EDIT, CONFIG_EDIT),
                        AccessTypeDefinition.of(HEALTH, "Health checks"), AccessTypeDefinition.of(METRICS, "Read metrics"),
                        AccessTypeDefinition.of(ADMIN_READ, "Read administration", HEALTH, METRICS),
                        AccessTypeDefinition.of(SECURITY, "Security settings"),
                        AccessTypeDefinition.of(ADMIN, "Administer cluster", ADMIN_READ, SECURITY, ADMIN_COLLECTION))
                .conditions(ConditionDefinition.of(IP_RANGE, "Client addresses", IP_RANGE))
                .configFields(
                        ConfigField.builder(SolrSettings.URLS).label("Solr URLs").type(ConfigFieldType.STRING).mandatory()
                                .description("A node's base URL, such as http://solr1.example.com:8983/solr; several separated by commas").build(),
                        ConfigField.builder(SolrSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(SolrSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(SolrSettings.NONE)
                                .description("none, basic (user name and password) or jwt (a bearer token)").build(),
                        ConfigField.builder(SolrSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("basic: the lookup user").build(),
                        ConfigField.builder(SolrSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("basic: the lookup user's password").build(),
                        ConfigField.builder(SolrSettings.TOKEN_FIELD).label("Token").type(ConfigFieldType.SECRET)
                                .description("jwt: the lookup user's JSON web token").build(),
                        ConfigField.builder(SolrSettings.TRUST_STORE).label("Trust store").type(ConfigFieldType.STRING)
                                .description("https: path on the GrantForge server of the trust store the nodes' certificates are checked against;"
                                        + " empty uses the JVM's").build(),
                        ConfigField.builder(SolrSettings.TRUST_STORE_PASSWORD).label("Trust store password").type(ConfigFieldType.SECRET)
                                .description("https: the trust store's password, if it has one").build(),
                        ConfigField.builder(SolrSettings.TRUST_STORE_TYPE).label("Trust store type").type(ConfigFieldType.STRING)
                                .defaultValue(SolrSettings.DEFAULT_TRUST_STORE_TYPE).description("https: PKCS12 or JKS").build(),
                        ConfigField.builder(SolrSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(SolrSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return SolrSettings.problems(config);
    }

    /** Signs in and describes the cluster: its Solr release, its mode and how many collections it has. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return clients.apply(SolrSettings.of(config)).run(node -> {
                NamedList<Object> system = SolrLookup.system(node);
                Object lucene = system.get("lucene");
                Object version = lucene instanceof NamedList<?> list ? list.get("solr-spec-version") : null;
                boolean cloud = "solrcloud".equals(String.valueOf(system.get("mode")));
                int collections = SolrLookup.collections(node).size();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "Solr " + version + (cloud ? " in SolrCloud mode, " : " without SolrCloud, ")
                        + collections + (cloud ? collections == 1 ? " collection" : " collections" : collections == 1 ? " core" : " cores"));
            });
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /** Lists the collections, or the request paths of the collections the policy names, that start with what was typed. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        SolrSettings settings;
        SolrLookup lookup;
        try {
            settings = SolrSettings.of(request.config());
            lookup = SolrLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        if (CLUSTER.equals(lookup.resource())) {
            return CLUSTER_VALUE.startsWith(lookup.typed()) ? List.of(CLUSTER_VALUE) : List.of();
        }
        return clients.apply(settings).run(lookup::run);
    }
}
