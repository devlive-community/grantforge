// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Elasticsearch plugin against a real cluster of one Elasticsearch release with security on: the connection test
 * with the license, lookups of indices, data streams, fields and the cluster within what a policy names, going to the
 * next node when one is down, refused sign-ins and missing privileges, and TLS checked in each verification mode, with
 * a password, an API key or a client certificate as the sign-in.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class ElasticsearchPluginContract
{
    private static final String READER = "reader";
    private static final String READER_PASSWORD = "Correct-Horse-Battery-31!";
    private static final String NOBODY = "nobody";
    private static final String NOBODY_PASSWORD = "Plain-Staple-Orbit-47!";

    private final ElasticsearchProvider provider = new ElasticsearchProvider();
    private @Nullable ElasticsearchNode node;

    /** The certificates; the cluster starts before the instance's own directories exist. */
    @TempDir
    static Path files;

    private String apiKey = "";

    /**
     * The release under test.
     *
     * @return such as {@code 9.5.5}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        ElasticsearchNode started = new ElasticsearchNode(version(), new TestCertificates(files));
        node = started;
        started.call("PUT", "/logs-eu", """
                {"mappings": {"properties": {"region": {"type": "text", "fields": {"keyword": {"type": "keyword"}}}, "card": {"type": "keyword"},
                 "address": {"properties": {"city": {"type": "keyword"}, "zip": {"type": "keyword"}}}}}}""");
        started.call("PUT", "/logs-us", """
                {"mappings": {"properties": {"region": {"type": "keyword"}, "amount": {"type": "long"}}}}""");
        started.call("PUT", "/metrics", """
                {"mappings": {"properties": {"cpu": {"type": "double"}}}}""");
        started.call("PUT", "/.internal-state", "{\"settings\": {\"index.hidden\": true}}");
        started.call("PUT", "/_index_template/app", """
                {"index_patterns": ["app-*"], "data_stream": {}, "template": {"mappings": {"properties": {"message": {"type": "text"}}}}}""");
        started.call("PUT", "/_data_stream/app-web", "");
        // The reader reads every index but not the cluster's release, which needs the monitor cluster privilege.
        started.call("PUT", "/_security/role/lookup", "{\"indices\": [{\"names\": [\"*\"], \"privileges\": [\"read\", \"view_index_metadata\"],"
                + " \"allow_restricted_indices\": false}]}");
        started.call("PUT", "/_security/user/" + READER, "{\"password\": \"" + READER_PASSWORD + "\", \"roles\": [\"lookup\"]}");
        started.call("PUT", "/_security/user/" + NOBODY, "{\"password\": \"" + NOBODY_PASSWORD + "\", \"roles\": []}");
        started.call("PUT", "/_security/role_mapping/client", "{\"enabled\": true, \"roles\": [\"superuser\"], \"rules\": {\"field\":"
                + " {\"username\": \"" + ElasticsearchNode.CERTIFICATE_USER + "\"}}}");
        String created = started.call("POST", "/_security/api_key", "{\"name\": \"grantforge-lookup\"}");
        apiKey = new ObjectMapper().readTree(created).path("encoded").asText();
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private ElasticsearchNode node()
    {
        return requireNonNull(node, "node");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(ElasticsearchSettings.HOSTS, node().url(), ElasticsearchSettings.USER, ElasticsearchNode.ADMIN,
                ElasticsearchSettings.PASSWORD, ElasticsearchNode.ADMIN_PASSWORD, ElasticsearchSettings.CERTIFICATE_AUTHORITIES, node().caCertificate(),
                ElasticsearchSettings.TIMEOUT, "10000"));
        for (int index = 0; index < more.length; index += 2) {
            if (more[index + 1].isEmpty()) {
                values.remove(more[index]);
            }
            else {
                values.put(more[index], more[index + 1]);
            }
        }
        return new ServiceConfig("search", values);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    @Test
    void theConnectionTestSignsInAndDescribesTheCluster()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("Elasticsearch " + version() + ", cluster docker-cluster, trial license, signed in as elastic, ")
                .endsWith(" indices and data streams");
        ConnectionResult reader = provider.testConnection(config(ElasticsearchSettings.USER, READER, ElasticsearchSettings.PASSWORD, READER_PASSWORD));
        assertThat(reader.message()).as(String.valueOf(reader.message())).startsWith("Elasticsearch (the user may not read the cluster's release")
                .contains("signed in as " + READER);
    }

    @Test
    void signsInWithAnApiKey()
    {
        ServiceConfig key = config(ElasticsearchSettings.AUTHENTICATION, ElasticsearchSettings.API_KEY_SIGN_IN, ElasticsearchSettings.USER, "",
                ElasticsearchSettings.PASSWORD, "", ElasticsearchSettings.API_KEY, apiKey);
        assertThat(provider.testConnection(key).message()).as("an API key signs in as the user who created it").contains("signed in as elastic");
        assertThat(lookup(key, ElasticsearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        ServiceConfig revoked = config(ElasticsearchSettings.AUTHENTICATION, ElasticsearchSettings.API_KEY_SIGN_IN, ElasticsearchSettings.USER, "",
                ElasticsearchSettings.PASSWORD, "", ElasticsearchSettings.API_KEY,
                Base64.getEncoder().encodeToString("no-such:key".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> lookup(revoked, ElasticsearchProvider.INDEX, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void looksUpIndicesDataStreamsFieldsAndTheCluster()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, ElasticsearchProvider.INDEX, "logs", Map.of())).containsExactly("logs-eu", "logs-us");
        assertThat(lookup(config, ElasticsearchProvider.INDEX, "", Map.of())).as("data streams stand for their backing indices; hidden ones are left out")
                .contains("app-web", "logs-eu", "logs-us", "metrics").noneMatch(name -> name.startsWith(".ds-") || name.equals(".internal-state"));
        assertThat(lookup(config, ElasticsearchProvider.INDEX, ".int", Map.of())).as("typing a dot asks for hidden indices")
                .containsExactly(".internal-state");
        assertThat(lookup(config, ElasticsearchProvider.INDEX, "a,b", Map.of())).as("no index name holds a comma").isEmpty();
        assertThat(lookup(config, ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("logs-eu"))))
                .as("objects, multi-fields and metadata fields are left out").containsExactly("address.city", "address.zip", "card", "region");
        assertThat(lookup(config, ElasticsearchProvider.FIELD, "a", Map.of(ElasticsearchProvider.INDEX, List.of("logs-??"))))
                .containsExactly("address.city", "address.zip", "amount");
        assertThat(lookup(config, ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("app-web")))).contains("message");
        assertThat(lookup(config, ElasticsearchProvider.FIELD, "c", Map.of())).containsExactly("card", "cpu");
        assertThat(lookup(config, ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("nothing-*")))).isEmpty();
        assertThat(lookup(config, ElasticsearchProvider.CLUSTER, "", Map.of())).containsExactly("docker-cluster");
        assertThat(lookup(config, ElasticsearchProvider.CLUSTER, "x", Map.of())).isEmpty();
        assertThatThrownBy(() -> lookup(config, "tenant", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserNeedsToReadTheIndices()
    {
        ServiceConfig reader = config(ElasticsearchSettings.USER, READER, ElasticsearchSettings.PASSWORD, READER_PASSWORD);
        assertThat(lookup(reader, ElasticsearchProvider.INDEX, "logs", Map.of())).containsExactly("logs-eu", "logs-us");
        assertThat(lookup(reader, ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("logs-us")))).containsExactly("amount", "region");
        ServiceConfig nobody = config(ElasticsearchSettings.USER, NOBODY, ElasticsearchSettings.PASSWORD, NOBODY_PASSWORD);
        assertThatThrownBy(() -> lookup(nobody, ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("logs-us"))))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }

    @Test
    void goesToTheNextNodeWhenOneCannotBeReached()
    {
        ServiceConfig config = config(ElasticsearchSettings.HOSTS, "https://127.0.0.1:1," + node().url());
        assertThat(lookup(config, ElasticsearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        ServiceConfig down = config(ElasticsearchSettings.HOSTS, "https://127.0.0.1:1,https://127.0.0.1:2");
        assertThatThrownBy(() -> lookup(down, ElasticsearchProvider.INDEX, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageContaining("127.0.0.1:1");
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(ElasticsearchSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, ElasticsearchProvider.INDEX, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ConnectionResult result = provider.testConnection(wrong);
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains(node().url()).contains("401");
    }

    @Test
    void checksTheNodesCertificatesAsTheVerificationModeSaysAndSignsInWithAClientCertificate()
    {
        assertThat(lookup(config(ElasticsearchSettings.VERIFICATION_MODE, "full"), ElasticsearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        assertThat(lookup(config(ElasticsearchSettings.VERIFICATION_MODE, "certificate"), ElasticsearchProvider.INDEX, "met", Map.of()))
                .containsExactly("metrics");
        assertThat(lookup(config(ElasticsearchSettings.VERIFICATION_MODE, "none", ElasticsearchSettings.CERTIFICATE_AUTHORITIES, ""),
                ElasticsearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        ServiceConfig untrusted = config(ElasticsearchSettings.CERTIFICATE_AUTHORITIES, "");
        assertThatThrownBy(() -> lookup(untrusted, ElasticsearchProvider.INDEX, "", Map.of())).as("the JVM does not trust the test CA")
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED))
                .hasMessageContaining("TLS failed");
        ServiceConfig certificate = config(ElasticsearchSettings.AUTHENTICATION, ElasticsearchSettings.CERTIFICATE_SIGN_IN, ElasticsearchSettings.USER, "",
                ElasticsearchSettings.PASSWORD, "", ElasticsearchSettings.CERTIFICATE, node().clientCertificate(), ElasticsearchSettings.KEY, node().clientKey());
        assertThat(provider.testConnection(certificate).message()).contains("signed in as " + ElasticsearchNode.CERTIFICATE_USER);
        assertThat(lookup(certificate, ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("metrics")))).containsExactly("cpu");
        ServiceConfig missingKey = config(ElasticsearchSettings.AUTHENTICATION, ElasticsearchSettings.CERTIFICATE_SIGN_IN, ElasticsearchSettings.USER, "",
                ElasticsearchSettings.PASSWORD, "", ElasticsearchSettings.CERTIFICATE, node().clientCertificate(), ElasticsearchSettings.KEY,
                files.resolve("missing.pem").toString());
        assertThat(provider.testConnection(missingKey).message()).contains("cannot be read");
    }
}
