// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.handler.ssl.SslContext;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Where the proxy connects to and how its clients connect to it.
 *
 * @param host the MySQL server's host
 * @param port its port
 * @param backendTls TLS toward the server, or {@code null} for none
 * @param clientTls TLS the proxy offers clients, or {@code null} for none
 * @param clientTlsRequired whether clients must use TLS
 * @param connectTimeoutMillis how long to wait for the server to accept a connection
 */
public record Backend(String host, int port, @Nullable SslContext backendTls, @Nullable SslContext clientTls, boolean clientTlsRequired,
        int connectTimeoutMillis)
{
    /** Checks the parts. */
    public Backend
    {
        requireNonNull(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("the MySQL port must be between 1 and 65535");
        }
        if (clientTlsRequired && clientTls == null) {
            throw new IllegalArgumentException("clients cannot be required to use TLS the proxy does not offer");
        }
    }
}
