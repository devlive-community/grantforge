// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ClientException;
import com.clickhouse.client.api.ClientMisconfigurationException;
import com.clickhouse.client.api.ConnectionInitiationException;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.query.GenericRecord;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickHouseNodeClientTest
{
    private static ClickHouseSettings settings(String urls)
    {
        return ClickHouseSettings.of(ClickHouseSettingsTest.config(ClickHouseSettings.URLS, urls));
    }

    private static LookupException.Reason reason(Throwable failed)
    {
        return ClickHouseNodeClient.failure("http://c1", failed).getReason();
    }

    @Test
    void asksTheFirstServerThatCanBeReached()
    {
        Client down = mock(Client.class);
        when(down.queryAll(anyString(), anyMap(), any())).thenThrow(new ConnectionInitiationException("refused", new ConnectException("refused")));
        Client up = mock(Client.class);
        GenericRecord row = mock(GenericRecord.class);
        when(row.getString("value")).thenReturn("sales");
        when(up.queryAll(anyString(), anyMap(), any())).thenReturn(List.of(row));
        List<String> asked = new ArrayList<>();
        ClickHouseNodeClient client = new ClickHouseNodeClient(settings("http://c1:8123,http://c2:8123"), url -> {
            asked.add(url);
            return url.contains("c1") ? down : up;
        });

        assertThat(ClickHouseNodeClient.texts(client.query("SELECT 1", Map.of()), "value")).containsExactly("sales");
        assertThat(asked).containsExactly("http://c1:8123", "http://c2:8123");
        verify(down).close();
        verify(up).close();
    }

    @Test
    void aServerThatAnswersDecides()
    {
        Client refusing = mock(Client.class);
        when(refusing.queryAll(anyString(), anyMap(), any())).thenThrow(new ServerException(516, "Code: 516. wrong password\nmore", 403, "q"));
        List<String> asked = new ArrayList<>();
        ClickHouseNodeClient client = new ClickHouseNodeClient(settings("http://c1:8123,http://c2:8123"), url -> {
            asked.add(url);
            return refusing;
        });
        assertThatThrownBy(() -> client.query("SELECT 1", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED))
                .hasMessage("http://c1:8123: Code: 516. wrong password");
        assertThat(asked).containsExactly("http://c1:8123");
    }

    @Test
    void reportsTheFirstServerWhenNoneCanBeReached()
    {
        Client down = mock(Client.class);
        when(down.queryAll(anyString(), anyMap(), any())).thenThrow(new ClientException("Read timed out"));
        ClickHouseNodeClient client = new ClickHouseNodeClient(settings("http://c1:8123,http://c2:8123"), url -> down);
        assertThatThrownBy(() -> client.query("SELECT 1", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageStartingWith("http://c1:8123: ");
    }

    @Test
    void namesWhyARequestFailed()
    {
        assertThat(reason(new ServerException(192, "unknown user", 403, "q"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new ServerException(497, "denied", 403, "q"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new ServerException(164, "readonly", 403, "q"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new ServerException(ServerException.CODE_UNKNOWN, "Bad Gateway", 502, "q"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new ServerException(ServerException.CODE_UNKNOWN, "Not Found", 404, "q"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new ServerException(62, "syntax", 400, "q"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new ClientException("x", new SSLHandshakeException("untrusted")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new ClientException("broken"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new IllegalStateException())).isEqualTo(LookupException.Reason.FAILED);
        assertThat(ClickHouseNodeClient.failure("http://c1", new IllegalStateException()).getMessage()).isEqualTo("http://c1: IllegalStateException");
    }

    @Test
    void buildsClientsForBothSignIns()
    {
        try (Client password = ClickHouseNodeClient.client(settings("http://c1:8123"), "http://c1:8123")) {
            assertThat(password.getEndpoints()).isNotEmpty();
        }
        ClickHouseSettings certificate = ClickHouseSettings.of(ClickHouseSettingsTest.config(ClickHouseSettings.URLS, "https://c1:8443",
                ClickHouseSettings.AUTHENTICATION, ClickHouseSettings.CERTIFICATE_SIGN_IN, ClickHouseSettings.CLIENT_CERTIFICATE, "/c.pem",
                ClickHouseSettings.CLIENT_KEY, "/k.pem", ClickHouseSettings.ROOT_CERTIFICATE, "/ca.pem"));
        assertThatThrownBy(() -> ClickHouseNodeClient.client(certificate, "https://c1:8443")).as("the files are read at once")
                .isInstanceOf(ClientMisconfigurationException.class);
        ClickHouseNodeClient client = new ClickHouseNodeClient(certificate);
        assertThatThrownBy(() -> client.query("SELECT 1", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED)).hasMessageContaining("SSL");
    }
}
