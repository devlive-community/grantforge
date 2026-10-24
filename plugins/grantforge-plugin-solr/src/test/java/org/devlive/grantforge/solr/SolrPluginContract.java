// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Solr plugin against a real SolrCloud node of one Solr release: the connection test, lookups of collections and of
 * their request paths, going to the next node when one is down, sign-ins and requests Solr refuses, and a node that
 * cannot be reached.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class SolrPluginContract
{
    private final SolrProvider provider = new SolrProvider();
    private @Nullable SolrCluster cluster;

    /**
     * The release under test.
     *
     * @return such as {@code 9.11.0}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        SolrCluster started = new SolrCluster(version());
        cluster = started;
        started.start(SolrCluster.ruleBasedAuthorization());
        started.user("reader", "readerpass");
        started.collection("sales");
        started.collection("hr");
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.close();
        }
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(SolrSettings.URLS, requireNonNull(cluster, "cluster").url(), SolrSettings.AUTHENTICATION,
                SolrSettings.BASIC, SolrSettings.USER, SolrCluster.ADMIN, SolrSettings.PASSWORD, SolrCluster.ADMIN_PASSWORD, SolrSettings.TIMEOUT, "10000"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("search", values);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    @Test
    void testsTheConnection()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(result.message()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("Solr " + version() + " in SolrCloud mode, 2 collections");
    }

    @Test
    void looksUpCollectionsAndTheirPaths()
    {
        assertThat(lookup(config(), "collection", "", Map.of())).containsExactly("hr", "sales");
        assertThat(lookup(config(), "path", "/", Map.of("collection", List.of("sal*")))).contains("/select", "/update", "/export", "/get");
        assertThat(lookup(config(), "path", "/sel", Map.of("collection", List.of("*")))).containsExactly("/select");
    }

    @Test
    void goesToTheNextNodeWhenOneIsDown()
    {
        ServiceConfig both = config(SolrSettings.URLS, "http://127.0.0.1:1/solr," + requireNonNull(cluster, "cluster").url(), SolrSettings.TIMEOUT,
                "3000");
        assertThat(lookup(both, "collection", "", Map.of())).containsExactly("hr", "sales");
    }

    @Test
    void namesARefusedSignInAndARefusedRequest()
    {
        assertThatThrownBy(() -> lookup(config(SolrSettings.PASSWORD, "wrong"), "collection", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThatThrownBy(() -> lookup(config(SolrSettings.USER, "reader", SolrSettings.PASSWORD, "readerpass"), "collection", "", Map.of()))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        assertThat(provider.testConnection(config(SolrSettings.PASSWORD, "wrong")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void namesANodeThatCannotBeReached()
    {
        ServiceConfig nowhere = config(SolrSettings.URLS, "http://127.0.0.1:1/solr", SolrSettings.TIMEOUT, "3000");
        assertThatThrownBy(() -> lookup(nowhere, "collection", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
