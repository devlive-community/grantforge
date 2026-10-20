// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LazyFuture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * A ResourceManager of one Hadoop release in a container, with a capacity scheduler of the queues
 * {@code root.default}, {@code root.sales} with {@code etl} and {@code adhoc} below it, and {@code root.hr}, and no
 * NodeManager: applications are accepted and wait. Releases the Apache project publishes as images run from them;
 * 3.2.4 is built from its SHA-512-verified Apache distribution, with the recipe the HDFS tests use, so both share one
 * image.
 */
final class YarnCluster
{
    static final int RPC = 8032;
    static final int ADMIN = 8033;

    private static final String JAVA8 = "eclipse-temurin:8-jdk-jammy@sha256:"
            + "a42a161e0edc7f0d3815bcfa22ce6d969fb48f69d0d17d4a1affd9800b455385";
    private static final String RECIPE_LABEL = "org.devlive.grantforge.hdfs.runtime.recipe.sha256";
    private static final Map<String, Future<String>> BUILT = new ConcurrentHashMap<>();

    private final String version;
    private final GenericContainer<?> container;
    private final Map<String, String> core = new LinkedHashMap<>();
    private final Map<String, String> yarn = new LinkedHashMap<>();

    /**
     * Prepares a ResourceManager.
     *
     * @param version such as {@code 3.4.3}
     */
    @SuppressWarnings("resource")
    YarnCluster(String version)
    {
        this.version = version;
        container = switch (version) {
            case "3.2.4" -> new GenericContainer<>(BUILT.computeIfAbsent(version, YarnCluster::verified));
            case "3.3.6" -> new GenericContainer<>(DockerImageName.parse("ghcr.io/apache/hadoop:3.3.6@sha256:"
                    + "ede68c4f0e730de6d1cab22cf2a44558bb9c61372b2caf409bbffd70f155fe17"))
                    // Apache publishes 3.3.6 for amd64 only; ARM hosts run it emulated.
                    .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));
            case "3.4.3" -> new GenericContainer<>(DockerImageName.parse("ghcr.io/apache/hadoop:3.4.3@sha256:"
                    + "2913159244f766a2e4acf370c3cef7a5c0292989b0085e66bc870ba27d576b57"));
            case "3.5.0" -> new GenericContainer<>(DockerImageName.parse("ghcr.io/apache/hadoop:3.5.0@sha256:"
                    + "389d4c48dcfdc34815d5ad1c4d2aa6ca85714c9c40e34d01ba5acb55b62f2d72"));
            default -> throw new IllegalArgumentException("no test image for Hadoop " + version);
        };
        container.withExposedPorts(RPC, ADMIN).withCommand("/opt/hadoop/bin/yarn", "resourcemanager")
                .waitingFor(Wait.forLogMessage(".*Transitioned to active state.*", 1).withStartupTimeout(Duration.ofMinutes(5)));
        yarn.put("yarn.resourcemanager.hostname", "localhost");
        yarn.put("yarn.resourcemanager.bind-host", "0.0.0.0");
        yarn.put("yarn.resourcemanager.scheduler.class", "org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler");
        yarn.put("yarn.resourcemanager.recovery.enabled", "false");
        yarn.put("yarn.webapp.ui2.enable", "false");
    }

    /**
     * Sets a property of {@code core-site.xml}.
     *
     * @param name the property
     * @param value its value
     * @return this cluster
     */
    YarnCluster core(String name, String value)
    {
        core.put(name, value);
        return this;
    }

    /**
     * Sets a property of {@code yarn-site.xml}.
     *
     * @param name the property
     * @param value its value
     * @return this cluster
     */
    YarnCluster yarn(String name, String value)
    {
        yarn.put(name, value);
        return this;
    }

    /**
     * Copies a file into the container.
     *
     * @param path where it goes
     * @param content its bytes
     * @return this cluster
     */
    YarnCluster file(String path, byte[] content)
    {
        container.withCopyToContainer(Transferable.of(content, 0644), path);
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

    /** Writes the configuration and starts the ResourceManager. */
    void start()
    {
        Map<String, String> scheduler = new LinkedHashMap<>();
        scheduler.put("yarn.scheduler.capacity.root.queues", "default,sales,hr");
        scheduler.put("yarn.scheduler.capacity.root.default.capacity", "40");
        scheduler.put("yarn.scheduler.capacity.root.sales.capacity", "40");
        scheduler.put("yarn.scheduler.capacity.root.hr.capacity", "20");
        scheduler.put("yarn.scheduler.capacity.root.sales.queues", "etl,adhoc");
        scheduler.put("yarn.scheduler.capacity.root.sales.etl.capacity", "50");
        scheduler.put("yarn.scheduler.capacity.root.sales.adhoc.capacity", "50");
        container.withCopyToContainer(Transferable.of(xml(core)), "/opt/hadoop/etc/hadoop/core-site.xml")
                .withCopyToContainer(Transferable.of(xml(yarn)), "/opt/hadoop/etc/hadoop/yarn-site.xml")
                .withCopyToContainer(Transferable.of(xml(scheduler)), "/opt/hadoop/etc/hadoop/capacity-scheduler.xml");
        container.start();
    }

    private static String xml(Map<String, String> properties)
    {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?>\n<configuration>\n");
        properties.forEach((name, value) -> xml.append("  <property><name>").append(name).append("</name><value>").append(value)
                .append("</value></property>\n"));
        return xml.append("</configuration>\n").toString();
    }

    /**
     * Returns the client address.
     *
     * @return {@code localhost:<port>}
     */
    String address()
    {
        return "localhost:" + container.getMappedPort(RPC);
    }

    /**
     * Returns the release.
     *
     * @return such as {@code 3.4.3}
     */
    String version()
    {
        return version;
    }

    /** Stops the ResourceManager. */
    void stop()
    {
        container.stop();
    }

    private static Future<String> verified(String version)
    {
        String dockerfile = "FROM " + JAVA8 + "\n"
                + "USER root\n"
                + "RUN apt-get update && apt-get install -y --no-install-recommends curl aria2 ca-certificates tar gzip procps"
                + " && rm -rf /var/lib/apt/lists/*\n"
                + "RUN aria2c --check-certificate=true --max-connection-per-server=8 --split=8 --min-split-size=8M"
                + " --max-tries=3 --retry-wait=3 --dir=/tmp --out=hadoop.tar.gz"
                + " https://archive.apache.org/dist/hadoop/common/hadoop-" + version + "/hadoop-" + version + ".tar.gz"
                + " && printf '%s  %s\\n' '"
                + "af45cbc9c8786dbe65c41be600f136218d4f3e8e4ec947c64c0bb4f919cedc91cdc5a55026f41b0037470c56ba7f625b29cb4935f7b6d5f0b242146c4e672af9"
                + "' /tmp/hadoop.tar.gz | sha512sum --check -"
                + " && mkdir -p /opt/hadoop && tar -xzf /tmp/hadoop.tar.gz --strip-components=1 -C /opt/hadoop"
                + " && rm /tmp/hadoop.tar.gz\n"
                + "RUN useradd --create-home --uid 1000 hadoop && mkdir -p /var/log/hadoop"
                + " && chown -R hadoop:hadoop /opt/hadoop /var/log/hadoop\n"
                + "ENV HADOOP_HOME=/opt/hadoop HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop HADOOP_LOG_DIR=/var/log/hadoop\n"
                + "ENV PATH=/opt/hadoop/bin:${PATH}\nWORKDIR /opt/hadoop\nUSER hadoop\n";
        String name = "grantforge/hadoop-it:" + version + "-java8";
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
                    // Only an absent image is built.
                }
                return new ImageFromDockerfile(name, false).withFileFromString("Dockerfile", dockerfile).withBuildImageCmdModifier(command -> {
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
}
