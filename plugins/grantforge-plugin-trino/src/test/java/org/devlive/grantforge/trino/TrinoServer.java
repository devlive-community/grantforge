// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A single-node Trino coordinator of one release in a container, for the integration tests, with the image's own
 * catalogs ({@code memory}, {@code tpch}, {@code tpcds} and {@code jmx}). Settings the tests add go to
 * {@code config.properties}; HTTPS, when a test enables it, listens on 8443 beside the plain HTTP the image uses.
 */
final class TrinoServer
{
    static final int HTTP = 8080;
    static final int HTTPS = 8443;

    private final String version;
    private final List<String> settings = new ArrayList<>(List.of("coordinator=true", "node-scheduler.include-coordinator=true",
            "http-server.http.port=" + HTTP, "discovery.uri=http://localhost:" + HTTP));
    private final GenericContainer<?> container;

    /**
     * Prepares a coordinator.
     *
     * @param version such as {@code 476}
     */
    @SuppressWarnings("resource")
    TrinoServer(String version)
    {
        this.version = version;
        container = new GenericContainer<>(DockerImageName.parse("trinodb/trino:" + version)).withExposedPorts(HTTP, HTTPS)
                .waitingFor(Wait.forLogMessage(".*======== SERVER STARTED ========.*", 1).withStartupTimeout(Duration.ofMinutes(4)));
    }

    /**
     * Adds a line to {@code config.properties}.
     *
     * @param line such as {@code http-server.https.enabled=true}
     * @return this server
     */
    TrinoServer set(String line)
    {
        settings.add(line);
        return this;
    }

    /**
     * Writes a file into {@code /etc/trino}.
     *
     * @param name such as {@code password.db}
     * @param content its bytes
     * @return this server
     */
    TrinoServer file(String name, byte[] content)
    {
        container.withCopyToContainer(Transferable.of(content, 0644), "/etc/trino/" + name);
        return this;
    }

    /**
     * Returns the container, to add files or environment before it starts.
     *
     * @return the container
     */
    GenericContainer<?> container()
    {
        return container;
    }

    /** Writes the settings and starts the coordinator. */
    void start()
    {
        container.withCopyToContainer(Transferable.of(String.join("\n", settings) + "\n"), "/etc/trino/config.properties");
        container.start();
    }

    /**
     * Returns the plain HTTP URL.
     *
     * @return {@code jdbc:trino://localhost:<port>}
     */
    String url()
    {
        return "jdbc:trino://localhost:" + container.getMappedPort(HTTP);
    }

    /**
     * Returns the HTTPS URL.
     *
     * @return {@code jdbc:trino://localhost:<port>}
     */
    String secureUrl()
    {
        return "jdbc:trino://localhost:" + container.getMappedPort(HTTPS);
    }

    /**
     * Returns the release.
     *
     * @return such as {@code 476}
     */
    String version()
    {
        return version;
    }

    /** Stops the coordinator. */
    void stop()
    {
        container.stop();
    }
}
