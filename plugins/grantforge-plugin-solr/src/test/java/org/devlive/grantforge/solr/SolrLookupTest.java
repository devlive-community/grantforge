// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolrLookupTest
{
    private final List<String> asked = new ArrayList<>();

    private static NamedList<Object> list(Object... pairs)
    {
        NamedList<Object> list = new NamedList<>();
        for (int index = 0; index < pairs.length; index += 2) {
            list.add(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return list;
    }

    /** A SolrCloud node with collections sales, hr and logs; hr's configuration may not be read and logs' fails. */
    private NamedList<Object> cloud(String path, SolrParams params, @Nullable String collection)
    {
        asked.add(collection == null ? path : collection + path);
        switch (path) {
            case "/admin/info/system":
                return list("mode", "solrcloud", "lucene", list("solr-spec-version", "9.11.0"));
            case "/admin/collections":
                return list("collections", List.of("sales", "hr", "logs"));
            default:
                if ("hr".equals(collection)) {
                    throw new SolrException(SolrException.ErrorCode.FORBIDDEN, "no");
                }
                if ("logs".equals(collection)) {
                    throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "broken");
                }
                Map<String, Object> handlers = new LinkedHashMap<>();
                handlers.put("/select", Map.of());
                handlers.put("/update", Map.of());
                handlers.put("/export", Map.of());
                return list("config", list("requestHandler", handlers));
        }
    }

    private static NamedList<Object> standalone(String path, SolrParams params, @Nullable String collection)
    {
        if ("/admin/info/system".equals(path)) {
            return list("mode", "std");
        }
        if ("/admin/cores".equals(path)) {
            return list("status", list("products", list(), "orders", list()));
        }
        return list("config", Map.of("requestHandler", list("/query", list())));
    }

    @Test
    void listsTheCollectionsOfACloudAndTheCoresOfANode() throws Exception
    {
        assertThat(SolrLookup.of("collection", "", Map.of(), 10).run(this::cloud)).containsExactly("hr", "logs", "sales");
        assertThat(SolrLookup.of("collection", " s", Map.of(), 10).run(this::cloud)).containsExactly("sales");
        assertThat(SolrLookup.of("collection", "", Map.of(), 1).run(this::cloud)).containsExactly("hr");
        assertThat(SolrLookup.of("collection", "", Map.of(), 10).run(SolrLookupTest::standalone)).containsExactly("orders", "products");
        assertThat(SolrLookup.cloud(this::cloud)).isTrue();
        assertThat(SolrLookup.cloud(SolrLookupTest::standalone)).isFalse();
    }

    @Test
    void listsThePathsOfTheCollectionsAPolicyNames() throws Exception
    {
        assertThat(SolrLookup.of("path", "/", Map.of("collection", List.of("sales")), 10).run(this::cloud)).containsExactly("/export", "/select", "/update");
        assertThat(SolrLookup.of("path", "/s", Map.of("collection", List.of("s*", "h?")), 10).run(this::cloud)).containsExactly("/select");
        assertThat(asked).contains("sales/config/requestHandler", "hr/config/requestHandler").doesNotContain("logs/config/requestHandler");
        assertThat(SolrLookup.of("path", "", Map.of(), 10).run(SolrLookupTest::standalone)).containsExactly("/query");
        assertThatThrownBy(() -> SolrLookup.of("path", "", Map.of("collection", List.of("*")), 10).run(this::cloud)).isInstanceOf(SolrException.class);
    }

    @Test
    void asksAboutAtMostSoManyCollections() throws Exception
    {
        List<String> many = new ArrayList<>();
        for (int index = 0; index < SolrLookup.MAX_ASKED + 5; index++) {
            many.add("c" + index);
        }
        int[] configs = {0};
        SolrLookup.of("path", "", Map.of(), 10).run((path, params, collection) -> {
            if ("/admin/info/system".equals(path)) {
                return list("mode", "solrcloud");
            }
            if ("/admin/collections".equals(path)) {
                return list("collections", many);
            }
            configs[0]++;
            return list("config", "none");
        });
        assertThat(configs[0]).isEqualTo(SolrLookup.MAX_ASKED);
    }

    @Test
    void hasTheClustersOneValueAndNoOtherLevel() throws Exception
    {
        assertThat(SolrLookup.of("cluster", "", Map.of(), 10).run(this::cloud)).containsExactly("*");
        assertThatIllegalArgumentException().isThrownBy(() -> SolrLookup.of("core", "", Map.of(), 10)).withMessageContaining("core");
        assertThat(SolrLookup.pattern("sa*").matcher("sales").matches()).isTrue();
        assertThat(SolrLookup.pattern("a.b").matcher("axb").matches()).isFalse();
    }
}
