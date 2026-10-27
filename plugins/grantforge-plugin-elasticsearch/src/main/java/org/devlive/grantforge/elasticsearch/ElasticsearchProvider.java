// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Elasticsearch service type in Elasticsearch's own terms. Indices and data streams hold fields, and the cluster
 * stands on its own; reading is decided by field, as field level security shows documents' fields, writing, deleting
 * and administering by index, and the cluster's own operations by cluster. The access types are Elasticsearch's own
 * index and cluster privileges, and names compare case-sensitively, as Elasticsearch compares them. Row filters of
 * indices are Elasticsearch queries, as document level security takes them. Elasticsearch masks no values, so the
 * service type offers no masking.
 */
public final class ElasticsearchProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "elasticsearch";
    static final String INDEX = "index";
    static final String FIELD = "field";
    static final String CLUSTER = "cluster";

    static final String READ = "read";
    static final String WRITE = "index";
    static final String DELETE = "delete";
    static final String CREATE_INDEX = "create_index";
    static final String DELETE_INDEX = "delete_index";
    static final String VIEW_METADATA = "view_index_metadata";
    static final String MONITOR = "monitor";
    static final String MANAGE = "manage";
    static final String CLUSTER_MONITOR = "cluster_monitor";
    static final String CREATE_SNAPSHOT = "create_snapshot";
    static final String MANAGE_SLM = "manage_slm";
    static final String MANAGE_TEMPLATES = "manage_index_templates";
    static final String MANAGE_PIPELINES = "manage_ingest_pipelines";
    static final String MANAGE_ILM = "manage_ilm";

    /** The licenses that have document and field level security, as Elasticsearch decides: Platinum and above. */
    static final Set<String> DLS_FLS_LICENSES = Set.of("platinum", "enterprise", "trial");

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Elasticsearch").description("Indices, data streams and fields of Elasticsearch clusters"
                        + " and the cluster as a whole")
                .resources(ResourceDefinition.builder(INDEX).label("Index or data stream").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true)
                                .accessTypes(WRITE, DELETE, CREATE_INDEX, DELETE_INDEX, VIEW_METADATA, MONITOR, MANAGE).build(),
                        ResourceDefinition.builder(FIELD).label("Field").parent(INDEX).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(READ).build(),
                        ResourceDefinition.builder(CLUSTER).label("Cluster").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(false)
                                .lookupSupported(true).accessTypes(CLUSTER_MONITOR, CREATE_SNAPSHOT, MANAGE_SLM, MANAGE_TEMPLATES, MANAGE_PIPELINES,
                                        MANAGE_ILM).build())
                .accessTypes(AccessTypeDefinition.of(READ, "Read"), AccessTypeDefinition.of(WRITE, "Index and update documents"),
                        AccessTypeDefinition.of(DELETE, "Delete documents"), AccessTypeDefinition.of(CREATE_INDEX, "Create indices"),
                        AccessTypeDefinition.of(DELETE_INDEX, "Delete indices"), AccessTypeDefinition.of(VIEW_METADATA, "View index metadata"),
                        AccessTypeDefinition.of(MONITOR, "Monitor indices"),
                        AccessTypeDefinition.of(MANAGE, "Manage indices", CREATE_INDEX, DELETE_INDEX, VIEW_METADATA, MONITOR),
                        AccessTypeDefinition.of(CLUSTER_MONITOR, "Monitor the cluster"),
                        AccessTypeDefinition.of(CREATE_SNAPSHOT, "Create snapshots"),
                        AccessTypeDefinition.of(MANAGE_SLM, "Manage snapshot lifecycle policies", CREATE_SNAPSHOT),
                        AccessTypeDefinition.of(MANAGE_TEMPLATES, "Manage index templates"),
                        AccessTypeDefinition.of(MANAGE_PIPELINES, "Manage ingest pipelines"),
                        AccessTypeDefinition.of(MANAGE_ILM, "Manage index lifecycle policies"))
                .rowFilter(new RowFilterDefinition(Set.of(INDEX)))
                .configFields(
                        ConfigField.builder(ElasticsearchSettings.HOSTS).label("Elasticsearch nodes").type(ConfigFieldType.STRING).mandatory()
                                .description("A node as a URL, such as https://es1.example.com:9200; several separated by commas").build(),
                        ConfigField.builder(ElasticsearchSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(ElasticsearchSettings.AUTHENTICATIONS.toArray(new String[0]))
                                .defaultValue(ElasticsearchSettings.PASSWORD_SIGN_IN)
                                .description("password, api_key, or certificate (a TLS client certificate that a PKI realm maps to a user)").build(),
                        ConfigField.builder(ElasticsearchSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("password: the lookup user; it needs to read the cluster's indices and their mappings").build(),
                        ConfigField.builder(ElasticsearchSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("password: the lookup user's password").build(),
                        ConfigField.builder(ElasticsearchSettings.API_KEY).label("API key").type(ConfigFieldType.SECRET)
                                .description("api_key: the encoded API key, as Elasticsearch returns it when it creates one").build(),
                        ConfigField.builder(ElasticsearchSettings.VERIFICATION_MODE).label("Certificate verification").type(ConfigFieldType.ENUM)
                                .options(ElasticsearchSettings.VERIFICATION_MODES.toArray(new String[0])).defaultValue(ElasticsearchSettings.FULL)
                                .description("As Kibana's verificationMode: full checks the nodes' certificates and names, certificate their"
                                        + " certificates, none nothing").build(),
                        ConfigField.builder(ElasticsearchSettings.CERTIFICATE_AUTHORITIES).label("CA certificates").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the PEM CA certificates the nodes are checked against, such as"
                                        + " Elasticsearch's http_ca.crt; empty uses the JVM's").build(),
                        ConfigField.builder(ElasticsearchSettings.CERTIFICATE).label("Client certificate").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the PEM client certificate").build(),
                        ConfigField.builder(ElasticsearchSettings.KEY).label("Client key").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the client certificate's PEM private key").build(),
                        ConfigField.builder(ElasticsearchSettings.KEY_PASSPHRASE).label("Client key passphrase").type(ConfigFieldType.SECRET)
                                .description("certificate: the passphrase of an encrypted private key").build(),
                        ConfigField.builder(ElasticsearchSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(ElasticsearchSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return ElasticsearchSettings.problems(config);
    }

    /**
     * Signs in and describes the cluster: its release and name, its license and whether that license has document and
     * field level security, whose user signed in and how many indices and data streams it has. A cluster without
     * security fails, as the agent enforces policies with Elasticsearch's own roles.
     */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            ElasticsearchSettings settings = ElasticsearchSettings.of(config);
            try (ElasticsearchClient client = ElasticsearchClient.open(settings)) {
                JsonNode root = readable(client, "/");
                if (root != null && "opensearch".equals(root.path("version").path("distribution").asText(""))) {
                    return ConnectionResult.failed("the nodes at " + String.join(",", settings.hosts()) + " are an OpenSearch cluster, not Elasticsearch");
                }
                JsonNode info = readable(client, "/_xpack");
                String release = root == null ? "Elasticsearch" : "Elasticsearch " + root.path("version").path("number").asText();
                if (info != null && !info.path("features").path("security").path("enabled").asBoolean(true)) {
                    return ConnectionResult.failed(release + " runs without security, which GrantForge enforces policies with");
                }
                JsonNode signedIn = client.get("/_security/_authenticate", Map.of());
                JsonNode resolved = client.get("/_resolve/index/*", Map.of("expand_wildcards", "open,closed"));
                long indices = 0;
                for (JsonNode index : resolved.path("indices")) {
                    if (index.path("data_stream").isMissingNode()) {
                        indices++;
                    }
                }
                indices += resolved.path("data_streams").size();
                String cluster = root == null ? release + " (the user may not read the cluster's release, which needs the monitor privilege)"
                        : release + ", cluster " + root.path("cluster_name").asText();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, cluster + license(info) + ", signed in as "
                        + signedIn.path("username").asText() + ", " + indices + (indices == 1 ? " index or data stream" : " indices and data streams"));
            }
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /**
     * Describes the cluster's license for the connection test.
     *
     * @param info what {@code GET /_xpack} returned, or {@code null} when the user may not read it
     * @return the license and, below Platinum, that row filters and field restrictions need more
     */
    static String license(@Nullable JsonNode info)
    {
        if (info == null) {
            return "";
        }
        JsonNode license = info.path("license");
        String type = license.path("type").asText("").toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return "";
        }
        if (!"active".equals(license.path("status").asText("active"))) {
            return ", " + type + " license " + license.path("status").asText() + ": row filters and field restrictions need an active Platinum or"
                    + " Enterprise license";
        }
        return DLS_FLS_LICENSES.contains(type) ? ", " + type + " license"
                : ", " + type + " license: row filters and field restrictions need Platinum or Enterprise";
    }

    /** What a read returns, or {@code null} when the user may not read it. */
    private static @Nullable JsonNode readable(ElasticsearchClient client, String endpoint)
    {
        try {
            return client.get(endpoint, Map.of());
        }
        catch (LookupException refused) {
            if (refused.getReason() == LookupException.Reason.ACCESS_DENIED) {
                return null;
            }
            throw refused;
        }
    }

    /** Lists the indices and data streams, fields or cluster that start with what was typed, within the indices the policy names. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        ElasticsearchSettings settings;
        ElasticsearchLookup lookup;
        try {
            settings = ElasticsearchSettings.of(request.config());
            lookup = ElasticsearchLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        try (ElasticsearchClient client = ElasticsearchClient.open(settings)) {
            return lookup.run(client);
        }
    }
}
