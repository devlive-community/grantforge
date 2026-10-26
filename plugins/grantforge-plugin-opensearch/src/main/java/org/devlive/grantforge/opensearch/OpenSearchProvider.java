// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

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
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The OpenSearch service type in OpenSearch's own terms. Indices and data streams hold fields, and the cluster stands
 * on its own; reading is decided by field, as the security plugin's field level security shows documents' fields,
 * writing, deleting and administering by index, and the cluster's own operations by cluster. Names compare
 * case-sensitively, as OpenSearch compares them. Row filters of indices are OpenSearch queries, as document level
 * security takes them, and masks are the security plugin's masked fields: hashes, or regular expressions that
 * replace characters.
 */
public final class OpenSearchProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "opensearch";
    static final String INDEX = "index";
    static final String FIELD = "field";
    static final String CLUSTER = "cluster";

    static final String READ = "read";
    static final String WRITE = "index";
    static final String DELETE = "delete";
    static final String CREATE_INDEX = "create_index";
    static final String MANAGE_ALIASES = "manage_aliases";
    static final String MONITOR = "monitor";
    static final String MANAGE = "manage";
    static final String CLUSTER_MONITOR = "cluster_monitor";
    static final String MANAGE_SNAPSHOTS = "manage_snapshots";
    static final String MANAGE_TEMPLATES = "manage_templates";
    static final String MANAGE_PIPELINES = "manage_pipelines";

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("OpenSearch").description("Indices, data streams and fields of OpenSearch clusters and the"
                        + " cluster as a whole")
                .resources(ResourceDefinition.builder(INDEX).label("Index or data stream").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true)
                                .accessTypes(WRITE, DELETE, CREATE_INDEX, MANAGE_ALIASES, MONITOR, MANAGE).build(),
                        ResourceDefinition.builder(FIELD).label("Field").parent(INDEX).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).accessTypes(READ).build(),
                        ResourceDefinition.builder(CLUSTER).label("Cluster").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(false)
                                .lookupSupported(true).accessTypes(CLUSTER_MONITOR, MANAGE_SNAPSHOTS, MANAGE_TEMPLATES, MANAGE_PIPELINES).build())
                .accessTypes(AccessTypeDefinition.of(READ, "Read"), AccessTypeDefinition.of(WRITE, "Index and update documents"),
                        AccessTypeDefinition.of(DELETE, "Delete documents"), AccessTypeDefinition.of(CREATE_INDEX, "Create indices"),
                        AccessTypeDefinition.of(MANAGE_ALIASES, "Manage aliases"), AccessTypeDefinition.of(MONITOR, "Monitor indices"),
                        AccessTypeDefinition.of(MANAGE, "Manage indices", CREATE_INDEX, MANAGE_ALIASES, MONITOR),
                        AccessTypeDefinition.of(CLUSTER_MONITOR, "Monitor the cluster"),
                        AccessTypeDefinition.of(MANAGE_SNAPSHOTS, "Manage snapshots and repositories"),
                        AccessTypeDefinition.of(MANAGE_TEMPLATES, "Manage index templates"),
                        AccessTypeDefinition.of(MANAGE_PIPELINES, "Manage ingest pipelines"))
                .dataMask(new DataMaskDefinition(Set.of(FIELD), List.of(
                        new MaskTypeDefinition("mask", "Redact", "{col}::/[A-Z]/::X::/[a-z]/::x::/[0-9]/::n"),
                        new MaskTypeDefinition("mask_show_last_4", "Partial mask: show last 4", "{col}::/.(?=.{4})/::x"),
                        new MaskTypeDefinition("mask_show_first_4", "Partial mask: show first 4", "{col}::/(?<=.{4})./::x"),
                        new MaskTypeDefinition("mask_hash", "Hash (SHA-256)", "{col}::SHA-256"),
                        new MaskTypeDefinition("mask_hash_salted", "Hash with the cluster's salt (BLAKE2b)", "{col}"),
                        new MaskTypeDefinition("mask_none", "Unmasked (retain original value)", null),
                        new MaskTypeDefinition("custom", "Custom", "{col}::{expr}"))))
                .rowFilter(new RowFilterDefinition(Set.of(INDEX)))
                .configFields(
                        ConfigField.builder(OpenSearchSettings.HOSTS).label("OpenSearch nodes").type(ConfigFieldType.STRING).mandatory()
                                .description("A node as a URL, such as https://opensearch1.example.com:9200; several separated by commas").build(),
                        ConfigField.builder(OpenSearchSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(OpenSearchSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(OpenSearchSettings.PASSWORD_SIGN_IN)
                                .description("password, or certificate (a TLS client certificate that the security plugin maps to a user)").build(),
                        ConfigField.builder(OpenSearchSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("password: the lookup user; it needs to read the cluster's indices and their mappings").build(),
                        ConfigField.builder(OpenSearchSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("password: the lookup user's password").build(),
                        ConfigField.builder(OpenSearchSettings.VERIFICATION_MODE).label("Certificate verification").type(ConfigFieldType.ENUM)
                                .options(OpenSearchSettings.VERIFICATION_MODES.toArray(new String[0])).defaultValue(OpenSearchSettings.FULL)
                                .description("As OpenSearch Dashboards' verificationMode: full checks the nodes' certificates and names,"
                                        + " certificate their certificates, none nothing").build(),
                        ConfigField.builder(OpenSearchSettings.CERTIFICATE_AUTHORITIES).label("CA certificates").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the PEM CA certificates the nodes are checked against; empty uses"
                                        + " the JVM's").build(),
                        ConfigField.builder(OpenSearchSettings.CERTIFICATE).label("Client certificate").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the PEM client certificate").build(),
                        ConfigField.builder(OpenSearchSettings.KEY).label("Client key").type(ConfigFieldType.STRING)
                                .description("certificate: path on the GrantForge server of the client certificate's PEM private key").build(),
                        ConfigField.builder(OpenSearchSettings.KEY_PASSPHRASE).label("Client key passphrase").type(ConfigFieldType.SECRET)
                                .description("certificate: the passphrase of an encrypted private key").build(),
                        ConfigField.builder(OpenSearchSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(OpenSearchSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return OpenSearchSettings.problems(config);
    }

    /**
     * Signs in and describes the cluster: its release and name, whose user signed in and how many indices and data
     * streams it has. A cluster without the security plugin fails, as the agent enforces policies through it.
     */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            OpenSearchSettings settings = OpenSearchSettings.of(config);
            try (OpenSearchClient client = OpenSearchClient.open(settings)) {
                JsonNode signedIn = client.find("/_plugins/_security/authinfo", Map.of());
                JsonNode root = description(client);
                if (root != null && !"opensearch".equals(root.path("version").path("distribution").asText(""))) {
                    return ConnectionResult.failed("the nodes at " + String.join(",", settings.hosts()) + " are no OpenSearch cluster");
                }
                if (signedIn == null) {
                    return ConnectionResult.failed((root == null ? "the cluster" : "OpenSearch " + root.path("version").path("number").asText())
                            + " runs without the security plugin, which GrantForge enforces policies with");
                }
                JsonNode resolved = client.get("/_resolve/index/*", Map.of("expand_wildcards", "open,closed"));
                long indices = 0;
                for (JsonNode index : resolved.path("indices")) {
                    if (index.path("data_stream").isMissingNode()) {
                        indices++;
                    }
                }
                indices += resolved.path("data_streams").size();
                String cluster = root == null ? "OpenSearch (the user may not read the cluster's release, cluster:monitor/main)"
                        : "OpenSearch " + root.path("version").path("number").asText() + ", cluster " + root.path("cluster_name").asText();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, cluster + ", signed in as " + signedIn.path("user_name").asText() + ", "
                        + indices + (indices == 1 ? " index or data stream" : " indices and data streams"));
            }
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /** The cluster's description, or {@code null} when the user may not read it. */
    private static @Nullable JsonNode description(OpenSearchClient client)
    {
        try {
            return client.get("/", Map.of());
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
        OpenSearchSettings settings;
        OpenSearchLookup lookup;
        try {
            settings = OpenSearchSettings.of(request.config());
            lookup = OpenSearchLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        try (OpenSearchClient client = OpenSearchClient.open(settings)) {
            return lookup.run(client);
        }
    }
}
