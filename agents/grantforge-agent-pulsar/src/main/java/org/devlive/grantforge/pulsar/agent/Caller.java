// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.broker.authentication.AuthenticationDataSource;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * Who asks: the role Pulsar's authentication gave the client, and where the request comes from. Pulsar has no groups of
 * its own, so a role's groups are those GrantForge puts it in. The client is the address that connected, which behind a
 * proxy is the proxy's unless the proxy forwards the client's own, with the HAProxy protocol or
 * {@code X-Forwarded-For}.
 *
 * @param role the role
 * @param clientIp the client's address, or {@code null} when unknown
 */
record Caller(String role, @Nullable String clientIp)
{
    /**
     * Returns who asks.
     *
     * @param role the role
     * @param authData what the client signed in with, or {@code null}
     * @return the caller
     */
    static Caller of(String role, @Nullable AuthenticationDataSource authData)
    {
        return new Caller(role, client(authData));
    }

    /**
     * Returns the address a request comes from.
     *
     * @param authData what the client signed in with, or {@code null}
     * @return the address, or {@code null} when it is not known
     */
    static @Nullable String client(@Nullable AuthenticationDataSource authData)
    {
        if (authData == null) {
            return null;
        }
        SocketAddress peer;
        try {
            if (!authData.hasDataFromPeer()) {
                return null;
            }
            peer = authData.getPeerAddress();
        }
        catch (RuntimeException unknown) {
            return null;
        }
        if (peer instanceof InetSocketAddress socket) {
            InetAddress address = socket.getAddress();
            return address == null ? null : address.getHostAddress();
        }
        return null;
    }

    /**
     * Returns the role.
     *
     * @return it, as policies name it
     */
    String user()
    {
        return role;
    }

    /**
     * Returns the groups Pulsar gives the role: none.
     *
     * @return no groups
     */
    List<String> groups()
    {
        return List.of();
    }
}
