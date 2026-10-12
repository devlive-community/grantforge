// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import org.devlive.grantforge.mysql.agent.sql.Fixtures;
import org.devlive.grantforge.mysql.agent.sql.Policies;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyServerTest
{
    @Test
    void bindsAnEphemeralPortAndClosingItAlsoClosesEstablishedConnections() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            assertThat(connection.proxy.address().getPort()).isBetween(1, 65535);
            assertThat(connection.proxy.address().getAddress().isLoopbackAddress()).isTrue();
            connection.stopProxy();
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
            assertThat(connection.server.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void bindFailureLeavesTheExistingListenerUsable() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        Backend backend = new Backend("127.0.0.1", 3306, null, null, false, 2000);
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), occupied.getLocalPort());
            assertThatThrownBy(() -> ProxyServer.start(address, backend, gate)).isInstanceOf(java.net.BindException.class);
            assertThat(occupied.isBound()).isTrue();
            assertThat(occupied.isClosed()).isFalse();
        }
    }

    @Test
    @SuppressWarnings("NullAway") // Invalid input is the behavior under test.
    void invalidConfigurationDoesNotStartListening()
    {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        assertThatNullPointerException().isThrownBy(() -> ProxyServer.start(address, null, gate)).withMessage("backend");
        assertThatNullPointerException().isThrownBy(() -> ProxyServer.start(address, new Backend("127.0.0.1", 3306, null, null, false, 2000), null))
                .withMessage("gate");
    }
}
