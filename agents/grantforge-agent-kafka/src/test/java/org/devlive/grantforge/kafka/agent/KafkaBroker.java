// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A single-node KRaft broker of one Kafka release in a container, for the integration tests. Its client listener uses
 * the same port inside and outside the container, so the address it advertises, {@code localhost:<port>}, is the one
 * clients reach, and the listener's security is whatever the test sets in Kafka's settings, written as the
 * {@code apache/kafka} image reads them from the environment.
 */
final class KafkaBroker
{
    private final int port;
    private final Map<String, String> settings = new LinkedHashMap<>();
    private final GenericContainer<?> container;

    /**
     * Prepares a broker.
     *
     * @param image such as {@code apache/kafka:4.3.1}
     * @param protocol the client listener's security protocol, such as {@code SASL_PLAINTEXT}
     * @throws IOException if no free port can be found
     */
    @SuppressWarnings("resource")
    KafkaBroker(String image, String protocol) throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        set("node.id", "1");
        set("process.roles", "broker,controller");
        set("listeners", "EXTERNAL://:" + port + ",CONTROLLER://:9093");
        set("advertised.listeners", "EXTERNAL://localhost:" + port);
        set("controller.listener.names", "CONTROLLER");
        set("inter.broker.listener.name", "EXTERNAL");
        set("listener.security.protocol.map", "EXTERNAL:" + protocol + ",CONTROLLER:PLAINTEXT");
        set("controller.quorum.voters", "1@localhost:9093");
        set("offsets.topic.replication.factor", "1");
        set("transaction.state.log.replication.factor", "1");
        set("transaction.state.log.min.isr", "1");
        set("group.initial.rebalance.delay.ms", "0");
        set("num.partitions", "1");
        container = new GenericContainer<>(DockerImageName.parse(image)).withExposedPorts(port)
                .withCreateContainerCmdModifier(command -> requireHostConfig(command.getHostConfig()).withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(port), new ExposedPort(port))))
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1).withStartupTimeout(Duration.ofMinutes(3)));
    }

    private static HostConfig requireHostConfig(@Nullable HostConfig config)
    {
        if (config == null) {
            throw new IllegalStateException("the container has no host configuration");
        }
        return config;
    }

    /**
     * Sets a broker setting.
     *
     * @param name Kafka's name for it, such as {@code sasl.enabled.mechanisms}
     * @param value the value
     * @return this broker
     */
    KafkaBroker set(String name, String value)
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

    /** Starts the broker and waits until it serves clients. */
    void start()
    {
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            container.withEnv(variable(setting.getKey()), setting.getValue());
        }
        container.start();
    }

    /**
     * Turns a setting's name into the image's environment variable: dots become underscores, underscores double and
     * dashes triple underscores.
     *
     * @param name the setting
     * @return the variable
     */
    static String variable(String name)
    {
        return "KAFKA_" + name.replace("_", "__").replace("-", "___").replace('.', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Returns where clients connect.
     *
     * @return {@code localhost:<port>}
     */
    String servers()
    {
        return "localhost:" + port;
    }

    /**
     * Returns the client listener's port.
     *
     * @return it
     */
    int port()
    {
        return port;
    }

    /** Stops the broker. */
    void stop()
    {
        container.stop();
    }
}
