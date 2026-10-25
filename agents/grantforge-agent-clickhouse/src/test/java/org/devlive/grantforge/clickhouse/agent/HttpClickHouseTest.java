// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ClientException;
import com.clickhouse.client.api.ClientMisconfigurationException;
import com.clickhouse.client.api.ConnectionInitiationException;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseColumn;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLHandshakeException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClickHouseTest
{
    private static AgentConfig.Connection connection(String... urls)
    {
        return new AgentConfig.Connection(List.of(urls), "grantforge", "secret", null, null, null, Duration.ofSeconds(5));
    }

    private static Client answering(List<GenericRecord> rows)
    {
        Client client = mock(Client.class);
        when(client.queryAll(anyString(), anyMap(), any())).thenReturn(rows);
        return client;
    }

    private static Client failing(RuntimeException failure)
    {
        Client client = mock(Client.class);
        when(client.queryAll(anyString(), anyMap(), any())).thenThrow(failure);
        return client;
    }

    @Test
    void asksTheServerThatAnsweredLastFirst()
    {
        Client down = failing(new ClientException("no", new ConnectionInitiationException("refused", new ConnectException("refused"))));
        Client up = answering(List.of());
        try (HttpClickHouse clickhouse = new HttpClickHouse(connection("http://c1:8123", "http://c2:8123"), url -> url.contains("c1") ? down : up)) {
            assertThat(clickhouse.query("SELECT 1", Map.of())).isEmpty();
            assertThat(clickhouse.preferred()).isEqualTo("http://c2:8123");
            clickhouse.execute("CREATE ROLE r");
            verify(down, times(1)).queryAll(anyString(), anyMap(), any());
            verify(up).queryAll(eq("CREATE ROLE r"), eq(Map.of()), any());
        }
        verify(down).close();
        verify(up).close();
    }

    @Test
    void aServerThatAnswersDecidesAndOneThatIsNotClickHouseDoesNot()
    {
        Client refusing = failing(new ServerException(497, "Code: 497. denied\nstack", 403, "q"));
        Client other = answering(List.of());
        try (HttpClickHouse clickhouse = new HttpClickHouse(connection("http://c1:8123", "http://c2:8123"), url -> url.contains("c1") ? refusing : other)) {
            assertThatThrownBy(() -> clickhouse.query("SELECT 1", Map.of())).isInstanceOfSatisfying(ClickHouseFailure.class,
                    failed -> assertThat(failed.code()).isEqualTo(497)).hasMessage("http://c1:8123: Code: 497. denied");
            verify(other, never()).queryAll(anyString(), anyMap(), any());
        }
        Client proxy = failing(new ServerException(ServerException.CODE_UNKNOWN, "Bad Gateway", 502, "q"));
        try (HttpClickHouse clickhouse = new HttpClickHouse(connection("http://c1:8123", "http://c2:8123"), url -> url.contains("c1") ? proxy : other)) {
            assertThat(clickhouse.query("SELECT 1", Map.of())).isEmpty();
        }
    }

    @Test
    void reportsTheFirstServerWhenNoneCanBeReached()
    {
        Client untrusted = failing(new ClientException("tls", new SSLHandshakeException("untrusted")));
        try (HttpClickHouse clickhouse = new HttpClickHouse(connection("https://c1:8443", "https://c2:8443"), url -> untrusted)) {
            assertThatThrownBy(() -> clickhouse.query("SELECT 1", Map.of())).isInstanceOfSatisfying(ClickHouseFailure.class,
                    failed -> assertThat(failed.code()).isEqualTo(ClickHouseFailure.UNREACHABLE)).hasMessageStartingWith("https://c1:8443: ");
        }
        try (HttpClickHouse none = new HttpClickHouse(connection(), url -> untrusted)) {
            assertThatThrownBy(() -> none.query("SELECT 1", Map.of())).hasMessage("no ClickHouse server is configured");
            assertThat(none.preferred()).isNull();
        }
        assertThat(HttpClickHouse.failure("http://c1", new IllegalStateException()).code()).isZero();
        assertThat(HttpClickHouse.message(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void raisesTheParsingLimitForLongStatements()
    {
        Client client = answering(List.of());
        try (HttpClickHouse clickhouse = new HttpClickHouse(connection("http://c1:8123"), url -> client)) {
            clickhouse.execute("GRANT " + "x".repeat(HttpClickHouse.DEFAULT_QUERY_SIZE));
            clickhouse.execute("GRANT y");
        }
        ArgumentCaptor<QuerySettings> settings = ArgumentCaptor.forClass(QuerySettings.class);
        verify(client, times(2)).queryAll(anyString(), anyMap(), settings.capture());
        assertThat(settings.getAllValues().get(0).getAllSettings().toString()).contains("max_query_size");
        assertThat(settings.getAllValues().get(1).getAllSettings().toString()).doesNotContain("max_query_size");
    }

    @Test
    void readsArraysAsListsAndNullAsNoValue()
    {
        GenericRecord record = mock(GenericRecord.class);
        TableSchema schema = mock(TableSchema.class);
        when(schema.getColumns()).thenReturn(List.of(ClickHouseColumn.of("xs", "Array(String)"), ClickHouseColumn.of("n", "Nullable(String)"),
                ClickHouseColumn.of("i", "Int64")));
        when(record.getSchema()).thenReturn(schema);
        when(record.hasValue("xs")).thenReturn(true);
        when(record.hasValue("i")).thenReturn(true);
        when(record.getList("xs")).thenReturn(new ArrayList<>(List.of("a", "b")));
        when(record.getObject("i")).thenReturn(5L);
        Row row = HttpClickHouse.row(record);
        assertThat(row.texts("xs")).containsExactly("a", "b");
        assertThat(row.text("n")).isNull();
        assertThat(row.number("i")).isEqualTo(5);
    }

    @Test
    void buildsClientsForBothSignIns()
    {
        HttpClickHouse.client(connection("http://c1:8123"), "http://c1:8123").close();
        AgentConfig.Connection noPassword = new AgentConfig.Connection(List.of("http://c1:8123"), "grantforge", null, null, null, null,
                Duration.ofSeconds(5));
        HttpClickHouse.client(noPassword, "http://c1:8123").close();
        AgentConfig.Connection certificate = new AgentConfig.Connection(List.of("https://c1:8443"), "grantforge", null, "/missing/ca.pem",
                "/missing/agent.pem", "/missing/agent.key", Duration.ofSeconds(5));
        assertThatThrownBy(() -> HttpClickHouse.client(certificate, "https://c1:8443")).isInstanceOf(ClientMisconfigurationException.class);
        new HttpClickHouse(connection("http://c1:8123")).close();
    }
}
