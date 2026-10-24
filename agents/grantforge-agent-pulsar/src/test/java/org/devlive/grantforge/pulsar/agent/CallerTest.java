// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.broker.authentication.AuthenticationDataSource;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallerTest
{
    private static AuthenticationDataSource peer(SocketAddress address)
    {
        AuthenticationDataSource data = mock(AuthenticationDataSource.class);
        when(data.hasDataFromPeer()).thenReturn(true);
        when(data.getPeerAddress()).thenReturn(address);
        return data;
    }

    @Test
    void takesTheAddressThatConnected()
    {
        Caller caller = Caller.of("alice", peer(new InetSocketAddress("10.1.2.3", 6650)));
        assertThat(caller.user()).isEqualTo("alice").isEqualTo(caller.role());
        assertThat(caller.clientIp()).isEqualTo("10.1.2.3");
        assertThat(caller.groups()).isEmpty();
        assertThat(Caller.client(peer(new InetSocketAddress("::1", 8080)))).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void knowsNoAddressWithoutAPeer()
    {
        assertThat(Caller.client(null)).isNull();
        AuthenticationDataSource none = mock(AuthenticationDataSource.class);
        assertThat(Caller.client(none)).isNull();
        assertThat(Caller.client(peer(InetSocketAddress.createUnresolved("broker", 6650)))).isNull();
        assertThat(Caller.client(peer(mock(SocketAddress.class)))).isNull();
        AuthenticationDataSource broken = mock(AuthenticationDataSource.class);
        when(broken.hasDataFromPeer()).thenThrow(new UnsupportedOperationException("no peer"));
        assertThat(Caller.client(broken)).isNull();
    }
}
