// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolrNodeClientTest
{
    @TempDir
    Path temporary;

    private static SolrSettings settings(String... values)
    {
        return SolrSettings.of(SolrSettingsTest.config(values));
    }

    private static LookupException.Reason reason(Throwable failed)
    {
        return SolrNodeClient.failure(failed).getReason();
    }

    private static NamedList<Object> answer(String key, Object value)
    {
        NamedList<Object> list = new NamedList<>();
        list.add(key, value);
        return list;
    }

    @Test
    void goesToTheNextNodeWhenOneCannotBeReached() throws Exception
    {
        SolrClient down = mock(SolrClient.class);
        SolrClient up = mock(SolrClient.class);
        when(down.request(any(), any())).thenThrow(new SolrServerException("down", new ConnectException("Connection refused")));
        when(up.request(any(), any())).thenReturn(answer("mode", "solrcloud"));
        List<String> asked = new ArrayList<>();
        SolrNodeClient client = new SolrNodeClient(settings(SolrSettings.URLS, "http://s1:8983/solr,http://s2:8983/solr"), (url, ignored) -> {
            asked.add(url);
            return url.contains("s1") ? down : up;
        });
        Object mode = client.run(node -> node.get("/admin/info/system", new ModifiableSolrParams(), null).get("mode"));
        assertThat(mode).isEqualTo("solrcloud");
        assertThat(asked).containsExactly("http://s1:8983/solr", "http://s2:8983/solr");
        verify(down).close();
        verify(up).close();
    }

    @Test
    void failsWhenNoNodeCanBeReachedAndAtOnceOtherwise() throws Exception
    {
        SolrClient down = mock(SolrClient.class);
        when(down.request(any(), any())).thenThrow(new SolrServerException("down", new UnknownHostException("s1")));
        SolrNodeClient none = new SolrNodeClient(settings(SolrSettings.URLS, "http://s1:8983/solr,http://s2:8983/solr"), (url, ignored) -> down);
        assertThatThrownBy(() -> none.run(node -> node.get("/admin/info/system", new ModifiableSolrParams(), null)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        SolrClient refusing = mock(SolrClient.class);
        when(refusing.request(any(), any())).thenThrow(new BaseHttpSolrClient.RemoteSolrException("http://s1", 403, "Unauthorized request", null));
        List<String> asked = new ArrayList<>();
        SolrNodeClient first = new SolrNodeClient(settings(SolrSettings.URLS, "http://s1:8983/solr,http://s2:8983/solr"), (url, ignored) -> {
            asked.add(url);
            return refusing;
        });
        assertThatThrownBy(() -> first.run(node -> node.get("/admin/collections", new ModifiableSolrParams(), null)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        assertThat(asked).hasSize(1);
    }

    @Test
    void sendsATokenWithEveryRequestAndAsksInTheCollection() throws Exception
    {
        SolrClient solr = mock(SolrClient.class);
        when(solr.request(any(), any())).thenReturn(new NamedList<>());
        SolrNodeClient client = new SolrNodeClient(settings(SolrSettings.AUTHENTICATION, "jwt", SolrSettings.TOKEN_FIELD, "eyJ.a.b"), (url, ignored) -> solr);
        client.run(node -> node.get("/config/requestHandler", new ModifiableSolrParams(), "sales"));
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<SolrRequest<?>> sent = (ArgumentCaptor) ArgumentCaptor.forClass(SolrRequest.class);
        verify(solr).request(sent.capture(), eq("sales"));
        assertThat(sent.getValue().getHeaders()).containsEntry("Authorization", "Bearer eyJ.a.b");
        assertThat(sent.getValue().getPath()).isEqualTo("/config/requestHandler");
        SolrNodeClient basic = new SolrNodeClient(settings(SolrSettings.AUTHENTICATION, "basic", SolrSettings.USER, "u", SolrSettings.PASSWORD, "p"),
                (url, ignored) -> solr);
        basic.run(node -> node.get("/admin/info/system", new ModifiableSolrParams(), null));
        verify(solr, never()).request(any(), eq("hr"));
    }

    @Test
    void namesWhyRequestsFail()
    {
        assertThat(reason(new BaseHttpSolrClient.RemoteSolrException("http://s1", 401, "require authentication", null)))
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new SolrException(SolrException.ErrorCode.NOT_FOUND, "no such collection"))).isEqualTo(LookupException.Reason.NOT_FOUND);
        assertThat(reason(new SolrServerException("wrapped", new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE, "gateway"))))
                .isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SolrException(SolrException.ErrorCode.SERVER_ERROR, "boom"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new IOException(new HttpConnectTimeoutException("slow")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new IllegalStateException("odd"))).isEqualTo(LookupException.Reason.FAILED);
        LookupException known = new LookupException(LookupException.Reason.NOT_FOUND, "gone");
        assertThat(SolrNodeClient.failure(known)).isSameAs(known);
        assertThat(SolrNodeClient.failure(new RuntimeException(null, new ConnectException("Connection\n refused")))).hasMessage("Connection refused");
        assertThat(SolrNodeClient.failure(new IllegalStateException())).hasMessage("IllegalStateException");
    }

    @Test
    void opensSolrjsOwnClientWithATrustStore() throws Exception
    {
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);
        Path trust = temporary.resolve("truststore.p12");
        try (OutputStream out = Files.newOutputStream(trust)) {
            empty.store(out, "changeit".toCharArray());
        }
        try (SolrClient solr = SolrNodeClient.open("https://s1:8983/solr", settings(SolrSettings.TRUST_STORE, trust.toString(),
                SolrSettings.TRUST_STORE_PASSWORD, "changeit", SolrSettings.AUTHENTICATION, "basic", SolrSettings.USER, "u", SolrSettings.PASSWORD, "p"))) {
            assertThat(solr).isNotNull();
        }
        try (SolrClient solr = SolrNodeClient.open("http://s1:8983/solr", settings())) {
            assertThat(solr).isNotNull();
        }
        assertThatThrownBy(() -> SolrNodeClient.open("https://s1:8983/solr", settings(SolrSettings.TRUST_STORE, temporary.resolve("missing").toString())))
                .isInstanceOf(IOException.class);
        SolrNodeClient broken = new SolrNodeClient(settings(SolrSettings.TRUST_STORE, temporary.resolve("missing").toString()));
        assertThatThrownBy(() -> broken.run(node -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
        assertThat(broken.toString()).contains("1 nodes");
    }
}
