// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Real Redis or Valkey servers in one container: a primary with one replica, or a cluster of three primaries with a
 * replica each. The default user has the password {@value #ROOT_PASSWORD}; the primary of a replicated deployment also
 * listens for TLS and keeps its ACL in a file. Every server is known by the same address inside and outside the container, so a client finds the
 * replica or the rest of the cluster from one node as it would in production.
 */
final class RedisNode
        implements AutoCloseable
{
    static final String ROOT_PASSWORD = "root-secret";

    private final List<Integer> ports = new ArrayList<>();
    private final int tlsPort;
    private final GenericContainer<?> container;

    /**
     * Starts the servers.
     *
     * @param image such as {@code redis:7.2.17} or {@code valkey/valkey:9.1.3}
     * @param cluster whether to start a cluster rather than a primary with a replica
     * @param certificates the CA and server certificate of the TLS the primary offers
     */
    @SuppressWarnings("resource")
    RedisNode(String image, boolean cluster, TestCertificates certificates) throws IOException, InterruptedException
    {
        for (int index = 0; index < (cluster ? 6 : 2); index++) {
            ports.add(free());
        }
        tlsPort = free();
        String common = " --requirepass " + ROOT_PASSWORD + " --masterauth " + ROOT_PASSWORD + " --bind 0.0.0.0 --protected-mode no --save '' --appendonly no";
        StringBuilder script = new StringBuilder("mkdir -p /data/nodes; cd /data/nodes; ");
        if (cluster) {
            for (int index = 0; index < ports.size(); index++) {
                int port = ports.get(index);
                // The bus port is the container's own, as the client port plus 10000 may not be a port at all.
                script.append("redis-server --port ").append(port).append(common).append(" --cluster-port ").append(17_001 + index)
                        .append(" --cluster-enabled yes --cluster-config-file nodes-").append(port)
                        .append(".conf --cluster-announce-ip 127.0.0.1 --daemonize yes --logfile /data/nodes/").append(port).append(".log; ");
            }
            for (int port : ports) {
                script.append("until redis-cli -p ").append(port).append(" -a ").append(ROOT_PASSWORD).append(" --no-auth-warning PING | grep -q PONG; do sleep 0.2; done; ");
            }
            script.append("yes yes | redis-cli -a ").append(ROOT_PASSWORD).append(" --no-auth-warning --cluster create ")
                    .append(ports.stream().map(port -> "127.0.0.1:" + port).collect(Collectors.joining(" "))).append(" --cluster-replicas 1; ");
        }
        else {
            script.append("echo 'user default on >").append(ROOT_PASSWORD).append(" ~* &* +@all' > /data/nodes/users.acl; ");
            script.append("redis-server --port ").append(ports.get(0)).append(common.replace(" --requirepass " + ROOT_PASSWORD, "")).append(" --aclfile /data/nodes/users.acl")
                    .append(" --tls-port ").append(tlsPort)
                    .append(" --tls-cert-file /certs/server.pem --tls-key-file /certs/server-key.pem --tls-ca-cert-file /certs/ca.pem --tls-auth-clients optional")
                    .append(" --daemonize yes --logfile /data/nodes/primary.log; ");
            script.append("redis-server --port ").append(ports.get(1)).append(common).append(" --replicaof 127.0.0.1 ").append(ports.get(0))
                    .append(" --replica-announce-ip 127.0.0.1 --daemonize yes --logfile /data/nodes/replica.log; ");
        }
        script.append("echo started; exec tail -f /dev/null");
        container = new GenericContainer<>(DockerImageName.parse(image).asCompatibleSubstituteFor("redis"))
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withUser("0").withEntrypoint("bash");
                    HostConfig host = cmd.getHostConfig();
                    List<Integer> bound = new ArrayList<>(ports);
                    bound.add(tlsPort);
                    if (host != null) {
                        host.withPortBindings(bound.stream().map(port -> new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", port), new ExposedPort(port))).toList());
                    }
                    cmd.withExposedPorts(bound.stream().map(ExposedPort::new).toList());
                })
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of(certificates.file("ca.pem")), 0644), "/certs/ca.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of(certificates.file("server.pem")), 0644), "/certs/server.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of(certificates.file("server-key.pem")), 0644), "/certs/server-key.pem")
                .withCommand("-c", script.toString())
                .waitingFor(Wait.forLogMessage(".*started.*", 1).withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        for (int port : ports) {
            awaitReady(port, cluster);
        }
    }

    private static int free() throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void awaitReady(int port, boolean cluster) throws IOException, InterruptedException
    {
        for (int tries = 0; tries < 300; tries++) {
            String info = cli(port, cluster ? "CLUSTER INFO" : "INFO replication");
            if (cluster ? info.contains("cluster_state:ok") : info.contains("role:master") || info.contains("master_link_status:up")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("node " + port + " is not ready");
    }

    /**
     * Returns the address of the first node, the primary of a replicated deployment.
     *
     * @return it as {@code host:port}
     */
    String address()
    {
        return "127.0.0.1:" + ports.get(0);
    }

    /**
     * Returns the addresses of every node.
     *
     * @return them as {@code host:port}
     */
    List<String> addresses()
    {
        return ports.stream().map(port -> "127.0.0.1:" + port).toList();
    }

    /**
     * Returns the address of the primary's TLS port.
     *
     * @return it as {@code localhost:port}, the name its certificate holds
     */
    String tlsAddress()
    {
        return "localhost:" + tlsPort;
    }

    /**
     * Runs a command as the default user on every node, as Redis keeps an ACL on each.
     *
     * @param command the command, its words separated by spaces
     */
    void everywhere(String command) throws IOException, InterruptedException
    {
        for (int port : ports) {
            cli(port, command);
        }
    }

    /**
     * Runs a command as the default user on one node.
     *
     * @param port the node's port
     * @param command the command, its words separated by spaces
     * @return what it printed
     */
    String cli(int port, String command) throws IOException, InterruptedException
    {
        List<String> words = new ArrayList<>(List.of("redis-cli", "-p", Integer.toString(port), "-a", ROOT_PASSWORD, "--no-auth-warning"));
        words.addAll(List.of(command.split(" ")));
        Container.ExecResult result = container.execInContainer(words.toArray(new String[0]));
        return result.getStdout() + result.getStderr();
    }

    /**
     * Runs a shell command in the container, such as a subscriber that keeps running.
     *
     * @param script the command
     */
    void shell(String script) throws IOException, InterruptedException
    {
        container.execInContainer("bash", "-c", script);
    }

    /**
     * Reads the primary's ACL file.
     *
     * @return what it holds
     */
    String aclFile() throws IOException, InterruptedException
    {
        return container.execInContainer("cat", "/data/nodes/users.acl").getStdout();
    }

    /**
     * Returns the ports of the nodes.
     *
     * @return them, the first node's first
     */
    List<Integer> ports()
    {
        return List.copyOf(ports);
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
