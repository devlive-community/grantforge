// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.server.embedded.ExitHandler;
import org.apache.zookeeper.server.embedded.ZooKeeperServerEmbedded;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/** A one-server ZooKeeper ensemble in the test's JVM, on a free port, with extra server settings. */
final class EmbeddedEnsemble
        implements AutoCloseable
{
    private final ZooKeeperServerEmbedded server;
    private final int port;

    EmbeddedEnsemble(Path directory, Map<String, String> settings) throws Exception
    {
        this.port = freePort();
        Properties configuration = new Properties();
        configuration.setProperty("clientPort", Integer.toString(port));
        configuration.setProperty("admin.enableServer", "false");
        configuration.setProperty("tickTime", "500");
        configuration.putAll(settings);
        server = ZooKeeperServerEmbedded.builder().baseDir(Files.createDirectories(directory)).configuration(configuration).exitHandler(ExitHandler.LOG_ONLY).build();
        server.start(30_000);
    }

    /** The connection string, as a service names the ensemble: {@code localhost:<port>}. */
    String connect()
    {
        return "localhost:" + port;
    }

    int port()
    {
        return port;
    }

    static int freePort() throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close()
    {
        server.close();
    }
}
