// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;

/**
 * A real single-member replica set with authorization on, a user {@code root} and TLS offered beside plain connections.
 * The member is known by the same address inside and outside the container, so a client finds it through the replica set
 * as it would in production.
 */
final class MongoNode
        implements AutoCloseable
{
    static final String ROOT_PASSWORD = "root-secret";
    static final String REPLICA_SET = "rs0";

    private final int port;
    private final GenericContainer<?> container;

    /**
     * Starts a replica set of MongoDB Community.
     *
     * @param version the release, such as {@code 8.0.34}
     * @param certificates the CA and server certificate of the TLS the member offers
     */
    MongoNode(String version, TestCertificates certificates) throws IOException, InterruptedException
    {
        this("mongo:" + version, certificates, null, "");
    }

    /**
     * Starts a replica set.
     *
     * @param image the image
     * @param certificates the CA and server certificate of the TLS the member offers
     * @param shared a directory of this machine to share as {@code /shared}, or {@code null}
     * @param options more options of {@code mongod}
     */
    @SuppressWarnings("resource")
    MongoNode(String image, TestCertificates certificates, @Nullable Path shared, String options) throws IOException, InterruptedException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        container = new GenericContainer<>(DockerImageName.parse(image).asCompatibleSubstituteFor("mongo"))
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withUser("0").withEntrypoint("bash");
                    HostConfig host = cmd.getHostConfig();
                    if (host != null) {
                        host.withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", port), new ExposedPort(port)));
                    }
                    cmd.withExposedPorts(new ExposedPort(port));
                })
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of(certificates.file("ca.pem")), 0644), "/certs/ca.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of(certificates.file("server-combined.pem")), 0644), "/certs/server.pem")
                .withCommand("-c", "head -c 64 /dev/urandom | base64 > /tmp/key; chmod 400 /tmp/key; mkdir -p /data/db; exec mongod --replSet " + REPLICA_SET
                        + " --keyFile /tmp/key --dbpath /data/db --bind_ip_all --port " + port + " --tlsMode allowTLS --tlsCertificateKeyFile /certs/server.pem"
                        + " --tlsCAFile /certs/ca.pem --tlsAllowConnectionsWithoutCertificates " + options)
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1).withStartupTimeout(Duration.ofMinutes(3)));
        if (shared != null) {
            container.withFileSystemBind(shared.toString(), "/shared");
        }
        container.start();
        // The first user comes through the localhost exception.
        shell(false, "rs.initiate({_id: '" + REPLICA_SET + "', members: [{_id: 0, host: '127.0.0.1:" + port + "'}]})");
        shell(false, "while (!db.hello().isWritablePrimary) { sleep(100); }; db.getSiblingDB('admin').createUser({user: 'root', pwd: '" + ROOT_PASSWORD
                + "', roles: ['root']})");
    }

    /**
     * Returns the connection string.
     *
     * @return it, finding the member through the replica set
     */
    String uri()
    {
        return "mongodb://127.0.0.1:" + port + "/?replicaSet=" + REPLICA_SET;
    }

    /**
     * Signs a user in.
     *
     * @param user the user
     * @param password its password
     * @param source the database it is defined in
     * @return a client of that user; close it
     */
    MongoClient client(String user, String password, String source)
    {
        return MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri()))
                .credential(MongoCredential.createScramSha256Credential(user, source, password.toCharArray())).build());
    }

    /**
     * Runs a script as {@code root}.
     *
     * @param script the JavaScript
     * @return what it printed
     */
    String run(String script) throws IOException, InterruptedException
    {
        return shell(true, script);
    }

    private String shell(boolean signIn, String script) throws IOException, InterruptedException
    {
        String[] command = signIn
                ? new String[] {"mongosh", "--quiet", "--port", Integer.toString(port), "-u", "root", "-p", ROOT_PASSWORD, "--authenticationDatabase", "admin", "--eval", script}
                : new String[] {"mongosh", "--quiet", "--port", Integer.toString(port), "--eval", script};
        Container.ExecResult result = container.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("mongosh failed: " + result.getStdout() + result.getStderr());
        }
        return result.getStdout();
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
