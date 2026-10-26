// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The OpenSearch plugin against a real cluster of one OpenSearch release with the security plugin: the connection
 * test, lookups of indices, data streams, fields and the cluster within what a policy names, going to the next node
 * when one is down, refused sign-ins and missing permissions, and TLS checked in each verification mode, with a
 * password or a client certificate as the sign-in.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class OpenSearchPluginContract
{
    private static final String READER = "reader";
    private static final String READER_PASSWORD = "Correct-Horse-Battery-31!";
    private static final String NOBODY = "nobody";
    private static final String NOBODY_PASSWORD = "Plain-Staple-Orbit-47!";

    private final OpenSearchProvider provider = new OpenSearchProvider();
    private @Nullable OpenSearchNode node;

    /** The certificates; the cluster starts before the instance's own directories exist. */
    @TempDir
    static Path files;

    /**
     * The release under test.
     *
     * @return such as {@code 3.9.0}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        OpenSearchNode started = new OpenSearchNode(version(), files);
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
        started.call("PUT", "/_plugins/_security/api/internalusers/" + READER, "{\"password\": \"" + READER_PASSWORD + "\"}");
        started.call("PUT", "/_plugins/_security/api/internalusers/" + NOBODY, "{\"password\": \"" + NOBODY_PASSWORD + "\"}");
        started.call("PUT", "/_plugins/_security/api/rolesmapping/readall", "{\"users\": [\"" + READER + "\"]}");
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private OpenSearchNode node()
    {
        return requireNonNull(node, "node");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(OpenSearchSettings.HOSTS, node().url(), OpenSearchSettings.USER, OpenSearchNode.ADMIN,
                OpenSearchSettings.PASSWORD, OpenSearchNode.ADMIN_PASSWORD, OpenSearchSettings.CERTIFICATE_AUTHORITIES, node().caCertificate(),
                OpenSearchSettings.TIMEOUT, "10000"));
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
        assertThat(result.message()).startsWith("OpenSearch " + version() + ", cluster docker-cluster, signed in as admin, ")
                .endsWith(" indices and data streams");
        ConnectionResult reader = provider.testConnection(config(OpenSearchSettings.USER, READER, OpenSearchSettings.PASSWORD, READER_PASSWORD));
        assertThat(reader.message()).as(String.valueOf(reader.message())).startsWith("OpenSearch (the user may not read the cluster's release")
                .contains("signed in as " + READER);
    }

    @Test
    void looksUpIndicesDataStreamsFieldsAndTheCluster()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, OpenSearchProvider.INDEX, "logs", Map.of())).containsExactly("logs-eu", "logs-us");
        assertThat(lookup(config, OpenSearchProvider.INDEX, "", Map.of())).as("data streams stand for their backing indices; hidden ones are left out")
                .contains("app-web", "logs-eu", "logs-us", "metrics").noneMatch(name -> name.startsWith(".ds-") || name.equals(".internal-state"));
        assertThat(lookup(config, OpenSearchProvider.INDEX, ".int", Map.of())).as("typing a dot asks for hidden indices")
                .containsExactly(".internal-state");
        assertThat(lookup(config, OpenSearchProvider.INDEX, "a,b", Map.of())).as("no index name holds a comma").isEmpty();
        assertThat(lookup(config, OpenSearchProvider.FIELD, "", Map.of(OpenSearchProvider.INDEX, List.of("logs-eu"))))
                .as("objects, multi-fields and metadata fields are left out").containsExactly("address.city", "address.zip", "card", "region");
        assertThat(lookup(config, OpenSearchProvider.FIELD, "a", Map.of(OpenSearchProvider.INDEX, List.of("logs-??"))))
                .containsExactly("address.city", "address.zip", "amount");
        assertThat(lookup(config, OpenSearchProvider.FIELD, "", Map.of(OpenSearchProvider.INDEX, List.of("app-web")))).contains("message");
        assertThat(lookup(config, OpenSearchProvider.FIELD, "c", Map.of())).containsExactly("card", "cpu");
        assertThat(lookup(config, OpenSearchProvider.FIELD, "", Map.of(OpenSearchProvider.INDEX, List.of("nothing-*")))).isEmpty();
        assertThat(lookup(config, OpenSearchProvider.CLUSTER, "", Map.of())).containsExactly("docker-cluster");
        assertThat(lookup(config, OpenSearchProvider.CLUSTER, "x", Map.of())).isEmpty();
        assertThatThrownBy(() -> lookup(config, "tenant", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserNeedsToReadTheIndices()
    {
        ServiceConfig reader = config(OpenSearchSettings.USER, READER, OpenSearchSettings.PASSWORD, READER_PASSWORD);
        assertThat(lookup(reader, OpenSearchProvider.INDEX, "logs", Map.of())).containsExactly("logs-eu", "logs-us");
        assertThat(lookup(reader, OpenSearchProvider.FIELD, "", Map.of(OpenSearchProvider.INDEX, List.of("logs-us")))).containsExactly("amount", "region");
        ServiceConfig nobody = config(OpenSearchSettings.USER, NOBODY, OpenSearchSettings.PASSWORD, NOBODY_PASSWORD);
        assertThatThrownBy(() -> lookup(nobody, OpenSearchProvider.FIELD, "", Map.of(OpenSearchProvider.INDEX, List.of("logs-us"))))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }

    @Test
    void goesToTheNextNodeWhenOneCannotBeReached()
    {
        ServiceConfig config = config(OpenSearchSettings.HOSTS, "https://127.0.0.1:1," + node().url());
        assertThat(lookup(config, OpenSearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        ServiceConfig down = config(OpenSearchSettings.HOSTS, "https://127.0.0.1:1,https://127.0.0.1:2");
        assertThatThrownBy(() -> lookup(down, OpenSearchProvider.INDEX, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageContaining("127.0.0.1:1");
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(OpenSearchSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, OpenSearchProvider.INDEX, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ConnectionResult result = provider.testConnection(wrong);
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains(node().url()).contains("401");
    }

    @Test
    void checksTheNodesCertificatesAsTheVerificationModeSaysAndSignsInWithAClientCertificate()
    {
        assertThat(lookup(config(OpenSearchSettings.VERIFICATION_MODE, "full"), OpenSearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        assertThat(lookup(config(OpenSearchSettings.VERIFICATION_MODE, "certificate"), OpenSearchProvider.INDEX, "met", Map.of()))
                .containsExactly("metrics");
        assertThat(lookup(config(OpenSearchSettings.VERIFICATION_MODE, "none", OpenSearchSettings.CERTIFICATE_AUTHORITIES, ""),
                OpenSearchProvider.INDEX, "met", Map.of())).containsExactly("metrics");
        ServiceConfig untrusted = config(OpenSearchSettings.CERTIFICATE_AUTHORITIES, "");
        assertThatThrownBy(() -> lookup(untrusted, OpenSearchProvider.INDEX, "", Map.of())).as("the JVM does not trust the demo CA")
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED))
                .hasMessageContaining("TLS failed");
        ServiceConfig certificate = config(OpenSearchSettings.AUTHENTICATION, OpenSearchSettings.CERTIFICATE_SIGN_IN, OpenSearchSettings.USER, "",
                OpenSearchSettings.PASSWORD, "", OpenSearchSettings.CERTIFICATE, node().clientCertificate(), OpenSearchSettings.KEY, node().clientKey());
        assertThat(provider.testConnection(certificate).message()).contains("signed in as " + OpenSearchNode.CERTIFICATE_USER);
        assertThat(lookup(certificate, OpenSearchProvider.FIELD, "", Map.of(OpenSearchProvider.INDEX, List.of("metrics")))).containsExactly("cpu");
        ServiceConfig missingKey = config(OpenSearchSettings.AUTHENTICATION, OpenSearchSettings.CERTIFICATE_SIGN_IN, OpenSearchSettings.USER, "",
                OpenSearchSettings.PASSWORD, "", OpenSearchSettings.CERTIFICATE, node().clientCertificate(), OpenSearchSettings.KEY,
                files.resolve("missing.pem").toString());
        assertThat(provider.testConnection(missingKey).message()).contains("cannot be read");
    }
}
