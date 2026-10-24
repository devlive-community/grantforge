// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SolrResourceTest
{
    @Test
    void decidesAPathAtItsCollectionAndThenWithThePath()
    {
        SolrResource path = SolrResource.collection("sales", "/select");
        assertThat(path.level()).isEqualTo("path");
        assertThat(path.display()).isEqualTo("collection sales path /select").isEqualTo(path.toString());
        assertThat(path.scopes()).containsExactly(Map.of("collection", "sales"), Map.of("collection", "sales", "path", "/select"));
        assertThat(List.copyOf(path.scopes().get(1).keySet())).containsExactly("collection", "path");
        assertThat(path.administered()).isTrue();
    }

    @Test
    void decidesACollectionAndTheCluster()
    {
        SolrResource collection = SolrResource.collection("hr", null);
        assertThat(collection.level()).isEqualTo("collection");
        assertThat(collection.display()).isEqualTo("collection hr");
        assertThat(collection.scopes()).containsExactly(Map.of("collection", "hr"));
        SolrResource cluster = SolrResource.cluster();
        assertThat(cluster.level()).isEqualTo("cluster");
        assertThat(cluster.display()).isEqualTo("cluster");
        assertThat(cluster.scopes()).containsExactly(Map.of("cluster", "*"));
        assertThat(cluster.administered()).isFalse();
    }
}
