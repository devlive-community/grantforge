// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.LazyFuture;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;

/**
 * An HBase cluster of one release in a container, for the integration tests: ZooKeeper, a master and a region server as
 * three daemons, on a local file system. Apache publishes no image, so the release is downloaded into a pinned Java 17
 * image and checked against its published SHA-512. Every daemon advertises {@code localhost} and listens on the same port
 * inside and outside the container, so the addresses clients find in ZooKeeper are the ones they reach.
 */
final class HBaseCluster
{
    /** The releases the tests cover, with the SHA-512 Apache publishes for each. */
    static final Map<String, String> RELEASES = Map.of(
            "2.5.16", "hbase-2.5.16-hadoop3-bin.tar.gz D7FFCBCD8C8905F0F72AF0D8C5E63CCC5221FD4CDA3183A79FFE19FE89245D8D8A2D3C7074AB58877A9C13DD4A9FBC3DC2EC56C7EA305082EABB02B3A7D1932F",
            "2.6.7", "hbase-2.6.7-hadoop3-bin.tar.gz 2E0835A3ED8E5A9114F5D878DEAFB2F7A6B1C8386BA422D1A307FCC5551D452DC7DA468729A43ACF75DCFE0B20424CB48F72ED424DFD15F81B1B3624454ED82D",
            "3.0.0", "hbase-3.0.0-bin.tar.gz 8290BC3CECE084D36F7F47AAE726268C7332E4828C6E8599DF1343B03348E93F91165FAD03AC6107BFBF0D8535E72024FA5D19217890D2848D013D8418A584ED");

    private static final String JAVA = "eclipse-temurin:17-jre-jammy@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1";
    private static final String RECIPE_LABEL = "org.devlive.grantforge.hbase.runtime.recipe.sha256";
    private static final Logger LOG = LoggerFactory.getLogger(HBaseCluster.class);
    private static final Map<String, Future<String>> IMAGES = new ConcurrentHashMap<>();
    private static final String START = """
            #!/bin/sh
            set -e
            cd /opt/hbase
            bin/hbase-daemon.sh start zookeeper
            bin/hbase-daemon.sh start master
            bin/hbase-daemon.sh start regionserver
            exec tail -n +1 -F logs/hbase-hbase-zookeeper-$(hostname).log logs/hbase-hbase-master-$(hostname).log \\
                logs/hbase-hbase-regionserver-$(hostname).log
            """;

    private final String version;
    private final int zookeeper;
    private final int master;
    private final int regionServer;
    private final Map<String, String> settings = new LinkedHashMap<>();
    private final Deque<String> logs = new ConcurrentLinkedDeque<>();
    private final GenericContainer<?> container;

    /**
     * Prepares a cluster.
     *
     * @param version one of {@link #RELEASES}
     * @throws IOException if no free ports can be found
     */
    @SuppressWarnings("resource")
    HBaseCluster(String version) throws IOException
    {
        String release = RELEASES.get(version);
        if (release == null) {
            throw new IllegalArgumentException("no verified HBase release " + version);
        }
        this.version = version;
        int[] ports = freePorts(3);
        zookeeper = ports[0];
        master = ports[1];
        regionServer = ports[2];
        set("hbase.cluster.distributed", "true");
        set("hbase.rootdir", "file:///opt/hbase-data/root");
        set("hbase.tmp.dir", "/opt/hbase-data/tmp");
        set("hbase.wal.provider", "filesystem");
        set("hbase.unsafe.stream.capability.enforce", "false");
        set("hbase.zookeeper.quorum", "localhost");
        set("hbase.zookeeper.property.clientPort", Integer.toString(zookeeper));
        set("hbase.zookeeper.property.dataDir", "/opt/hbase-data/zookeeper");
        set("hbase.master.port", Integer.toString(master));
        set("hbase.regionserver.port", Integer.toString(regionServer));
        set("hbase.master.info.port", "-1");
        set("hbase.regionserver.info.port", "-1");
        set("hbase.master.ipc.address", "0.0.0.0");
        set("hbase.regionserver.ipc.address", "0.0.0.0");
        set("hbase.master.hostname", "localhost");
        set("hbase.regionserver.hostname", "localhost");
        set("hbase.unsafe.regionserver.hostname", "localhost");
        String[] parts = release.split(" ");
        Future<String> image = IMAGES.computeIfAbsent(version, ignored -> image(dockerfile(parts[0], parts[1])));
        container = new GenericContainer<>(image).withExposedPorts(zookeeper, master, regionServer)
                .withEnv("HBASE_IDENT_STRING", "hbase").withEnv("HBASE_MANAGES_ZK", "false")
                .withCopyToContainer(Transferable.of(START, 0755), "/opt/hbase/start.sh")
                .withCommand("/opt/hbase/start.sh")
                .withCreateContainerCmdModifier(command -> {
                    List<PortBinding> bindings = new ArrayList<>();
                    for (int port : ports) {
                        bindings.add(new PortBinding(Ports.Binding.bindPort(port), new ExposedPort(port)));
                    }
                    requireHostConfig(command.getHostConfig()).withPortBindings(bindings);
                })
                .withLogConsumer(frame -> keep(frame.getUtf8StringWithoutLineEnding()))
                .waitingFor(Wait.forLogMessage(".*Master has completed initialization.*", 1).withStartupTimeout(Duration.ofMinutes(4)));
    }

    /** Keeps the last lines the daemons logged, to tell why a cluster did not start. */
    private void keep(String line)
    {
        if (line.contains("ERROR") || line.contains("Exception") || line.contains("Caused by")) {
            logs.addLast(line);
            while (logs.size() > 60) {
                logs.pollFirst();
            }
        }
    }

    private static int[] freePorts(int count) throws IOException
    {
        List<ServerSocket> sockets = new ArrayList<>();
        try {
            int[] ports = new int[count];
            for (int index = 0; index < count; index++) {
                ServerSocket socket = new ServerSocket(0);
                sockets.add(socket);
                ports[index] = socket.getLocalPort();
            }
            return ports;
        }
        finally {
            for (ServerSocket socket : sockets) {
                socket.close();
            }
        }
    }

    private static HostConfig requireHostConfig(@Nullable HostConfig config)
    {
        if (config == null) {
            throw new IllegalStateException("the container has no host configuration");
        }
        return config;
    }

    private static String dockerfile(String file, String checksum)
    {
        String path = file.replaceFirst("^hbase-([^-]+)-.*$", "$1") + "/" + file;
        return "FROM " + JAVA + "\n"
                + "RUN apt-get update && apt-get install -y --no-install-recommends aria2 ca-certificates tar gzip procps"
                + " && rm -rf /var/lib/apt/lists/*\n"
                + "RUN aria2c --check-certificate=true --max-connection-per-server=8 --split=8 --min-split-size=8M"
                + " --max-tries=3 --retry-wait=3 --dir=/tmp --out=hbase.tar.gz"
                + " https://dlcdn.apache.org/hbase/" + path + " https://archive.apache.org/dist/hbase/" + path
                + " && printf '%s  %s\\n' '" + checksum.toLowerCase(java.util.Locale.ROOT) + "' /tmp/hbase.tar.gz | sha512sum --check -"
                + " && mkdir -p /opt/hbase /opt/hbase-data && tar -xzf /tmp/hbase.tar.gz --strip-components=1 -C /opt/hbase"
                + " && rm -rf /tmp/hbase.tar.gz /opt/hbase/docs\n"
                + "ENV HBASE_HOME=/opt/hbase HBASE_LOG_DIR=/opt/hbase/logs\nWORKDIR /opt/hbase\n";
    }

    private Future<String> image(String dockerfile)
    {
        String name = "grantforge/hbase-it:" + version;
        String fingerprint = fingerprint(dockerfile);
        return new LazyFuture<>()
        {
            @Override
            protected String resolve()
            {
                try {
                    InspectImageResponse existing = DockerClientFactory.instance().client().inspectImageCmd(name).exec();
                    Map<String, String> labels = existing.getConfig() == null ? null : existing.getConfig().getLabels();
                    if (labels != null && fingerprint.equals(labels.get(RECIPE_LABEL))) {
                        return existing.getId();
                    }
                }
                catch (NotFoundException missing) {
                    // Only an absent image is built; other Docker failures propagate.
                }
                LOG.info("Building HBase {} test image: pinned Java 17 and the SHA-512-verified Apache release", version);
                return new ImageFromDockerfile(name, false).withFileFromString("Dockerfile", dockerfile)
                        .withBuildImageCmdModifier(command -> {
                            Map<String, String> labels = new HashMap<>();
                            if (command.getLabels() != null) {
                                labels.putAll(command.getLabels());
                            }
                            labels.put(RECIPE_LABEL, fingerprint);
                            command.withLabels(labels);
                        }).get();
            }
        };
    }

    private static String fingerprint(String recipe)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(recipe.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /**
     * Sets an HBase setting of every daemon.
     *
     * @param name HBase's name for it
     * @param value the value
     * @return this cluster
     */
    HBaseCluster set(String name, String value)
    {
        settings.put(name, value);
        return this;
    }

    /**
     * Returns the container, to add files or JVM options before it starts.
     *
     * @return the container
     */
    GenericContainer<?> container()
    {
        return container;
    }

    /** Writes the settings, to hbase-site.xml and core-site.xml, and starts the daemons, waiting until the master has initialized. */
    void start()
    {
        StringBuilder site = new StringBuilder("<?xml version=\"1.0\"?>\n<configuration>\n");
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            site.append("  <property><name>").append(setting.getKey()).append("</name><value>").append(setting.getValue())
                    .append("</value></property>\n");
        }
        site.append("</configuration>\n");
        // Hadoop's own sign-in reads core-site.xml, such as hadoop.security.authentication; both files get every setting.
        container.withCopyToContainer(Transferable.of(site.toString()), "/opt/hbase/conf/hbase-site.xml")
                .withCopyToContainer(Transferable.of(site.toString()), "/opt/hbase/conf/core-site.xml");
        try {
            container.start();
        }
        catch (RuntimeException failed) {
            throw new IllegalStateException("HBase " + version + " did not start; its last errors:\n" + String.join("\n", logs), failed);
        }
    }

    /**
     * Returns the ZooKeeper quorum clients start from.
     *
     * @return {@code localhost}
     */
    String quorum()
    {
        return "localhost";
    }

    /**
     * Returns ZooKeeper's client port.
     *
     * @return the port
     */
    int zookeeperPort()
    {
        return zookeeper;
    }

    /**
     * Returns the region server's address, which bootstraps clients without ZooKeeper.
     *
     * @return {@code localhost:<port>}
     */
    String regionServer()
    {
        return "localhost:" + regionServer;
    }

    /**
     * Returns the release.
     *
     * @return such as {@code 2.6.7}
     */
    String version()
    {
        return version;
    }

    /** Stops the cluster. */
    void stop()
    {
        container.stop();
    }
}
