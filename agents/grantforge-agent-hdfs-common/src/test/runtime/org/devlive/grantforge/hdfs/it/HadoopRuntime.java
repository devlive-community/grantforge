// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LazyFuture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/** Exact Hadoop/runtime/image combinations; old distributions are checksum-verified inside a pinned Java 8 image. */
final class HadoopRuntime
{
    private static final String JAVA8 = "eclipse-temurin:8-jdk-jammy@sha256:"
            + "a42a161e0edc7f0d3815bcfa22ce6d969fb48f69d0d17d4a1affd9800b455385";
    private static final String RECIPE_LABEL = "org.devlive.grantforge.hdfs.runtime.recipe.sha256";
    private static final Logger LOG = LoggerFactory.getLogger(HadoopRuntime.class);
    private static final Map<String, Future<String>> BUILT_IMAGES = new ConcurrentHashMap<>();

    private final String version;
    private final int javaMajor;
    private final String image;
    private final String checksum;
    private final boolean amd64;

    private HadoopRuntime(String version, int javaMajor, String image, String checksum, boolean amd64)
    {
        this.version = version;
        this.javaMajor = javaMajor;
        this.image = image;
        this.checksum = checksum;
        this.amd64 = amd64;
    }

    static HadoopRuntime configured()
    {
        String version = System.getProperty("grantforge.hdfs.it.hadoop.version", "3.5.0");
        return switch (version) {
            case "2.7.7" -> archived(version,
                    "17c8917211dd4c25f78bf60130a390f9e273b0149737094e45f4ae5c917b1174b97eb90818c5df068e607835120126281bcc07514f38bd7fd3cb8e9d3db1bdde");
            case "2.10.2" -> archived(version,
                    "13e95907073d815e3f86cdcc24193bb5eec0374239c79151923561e863326988c7f32a05fb7a1e5bc962728deb417f546364c2149541d6234221b00459154576");
            case "3.2.4" -> archived(version,
                    "af45cbc9c8786dbe65c41be600f136218d4f3e8e4ec947c64c0bb4f919cedc91cdc5a55026f41b0037470c56ba7f625b29cb4935f7b6d5f0b242146c4e672af9");
            case "3.3.6" -> new HadoopRuntime(version, 8, "ghcr.io/apache/hadoop:3.3.6@sha256:"
                    + "ede68c4f0e730de6d1cab22cf2a44558bb9c61372b2caf409bbffd70f155fe17", "", true);
            case "3.4.3" -> new HadoopRuntime(version, 11, "ghcr.io/apache/hadoop:3.4.3@sha256:"
                    + "2913159244f766a2e4acf370c3cef7a5c0292989b0085e66bc870ba27d576b57", "", false);
            case "3.5.0" -> new HadoopRuntime(version, 17, "ghcr.io/apache/hadoop:3.5.0@sha256:"
                    + "389d4c48dcfdc34815d5ad1c4d2aa6ca85714c9c40e34d01ba5acb55b62f2d72", "", false);
            default -> throw new IllegalArgumentException("no certified test image for Hadoop " + version);
        };
    }

    private static HadoopRuntime archived(String version, String checksum)
    {
        return new HadoopRuntime(version, 8, "", checksum, false);
    }

    GenericContainer<?> container()
    {
        GenericContainer<?> container;
        if (image.isEmpty()) {
            String dockerfile = dockerfile();
            // ImageFromDockerfile is a Future: all daemons and scenarios share one resolved image, rather than
            // rebuilding and downloading the verified distribution for each container. The key binds its full recipe.
            Future<String> built = BUILT_IMAGES.computeIfAbsent(version + "\n" + dockerfile,
                    ignored -> verifiedImage(dockerfile));
            container = new GenericContainer<>(built);
        }
        else {
            container = new GenericContainer<>(DockerImageName.parse(image));
        }
        if (amd64) {
            // Apache 3.3.6 publishes amd64 only; ARM hosts execute this explicit emulation row, not a native ARM certification.
            container.withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));
        }
        return container;
    }

    private Future<String> verifiedImage(String dockerfile)
    {
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
                        LOG.info("Reusing Hadoop {} test image with matching verified-build recipe {}", version, fingerprint);
                        // Returning the inspected immutable image id also protects against a subsequent tag replacement.
                        return existing.getId();
                    }
                }
                catch (NotFoundException missing) {
                    // Only a genuinely absent image starts a build. Docker connection and inspection failures propagate.
                }
                LOG.info("Building Hadoop {} test image: pinned Java 8 and SHA-512-verified Apache source, recipe {}", version, fingerprint);
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

    private String dockerfile()
    {
        return "FROM " + JAVA8 + "\n"
                + "USER root\n"
                + "RUN apt-get update && apt-get install -y --no-install-recommends curl aria2 ca-certificates tar gzip procps"
                + " && rm -rf /var/lib/apt/lists/*\n"
                + "RUN aria2c --check-certificate=true --max-connection-per-server=8 --split=8 --min-split-size=8M"
                + " --max-tries=3 --retry-wait=3 --dir=/tmp --out=hadoop.tar.gz"
                + " https://archive.apache.org/dist/hadoop/common/hadoop-" + version + "/hadoop-" + version + ".tar.gz"
                + " && printf '%s  %s\\n' '" + checksum + "' /tmp/hadoop.tar.gz | sha512sum --check -"
                + " && mkdir -p /opt/hadoop && tar -xzf /tmp/hadoop.tar.gz --strip-components=1 -C /opt/hadoop"
                + " && rm /tmp/hadoop.tar.gz\n"
                + "RUN useradd --create-home --uid 1000 hadoop && mkdir -p /var/log/hadoop"
                + " && chown -R hadoop:hadoop /opt/hadoop /var/log/hadoop\n"
                + "ENV HADOOP_HOME=/opt/hadoop HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop HADOOP_LOG_DIR=/var/log/hadoop\n"
                + "ENV PATH=/opt/hadoop/bin:${PATH}\nWORKDIR /opt/hadoop\nUSER hadoop\n";
    }

    String version()
    {
        return version;
    }

    int javaMajor()
    {
        return javaMajor;
    }

    int httpPort()
    {
        return version.startsWith("2.") ? 50070 : 9870;
    }

    Integer[] datanodePorts()
    {
        return version.startsWith("2.") ? new Integer[] {50010, 50020, 50075} : new Integer[] {9866, 9867, 9864};
    }
}
