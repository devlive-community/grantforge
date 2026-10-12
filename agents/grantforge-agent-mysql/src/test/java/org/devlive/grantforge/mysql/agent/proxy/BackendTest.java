// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

class BackendTest
{
    @Test
    @SuppressWarnings("NullAway") // Invalid input is the behavior under test.
    void requiresATcpEndpoint()
    {
        assertThatNullPointerException().isThrownBy(() -> new Backend(null, 3306, null, null, false, 2000));
        for (int port : new int[] {0, -1, 65536}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new Backend("mysql", port, null, null, false, 2000))
                    .withMessageContaining("between 1 and 65535");
        }
        assertThat(new Backend("mysql", 1, null, null, false, 2000).port()).isEqualTo(1);
        assertThat(new Backend("mysql", 65535, null, null, false, 2000).port()).isEqualTo(65535);
    }

    @Test
    void requiresClientTlsOnlyWhenTheProxyCanOfferIt()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> new Backend("mysql", 3306, null, null, true, 2000))
                .withMessageContaining("TLS the proxy does not offer");
        SslContext client = mock(SslContext.class);
        SslContext server = mock(SslContext.class);
        Backend settings = new Backend("mysql", 3306, server, client, true, 1500);
        assertThat(settings.clientTls()).isSameAs(client);
        assertThat(settings.backendTls()).isSameAs(server);
        assertThat(settings.clientTlsRequired()).isTrue();
        assertThat(settings.connectTimeoutMillis()).isEqualTo(1500);
    }
}
