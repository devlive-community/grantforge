// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Separate Hadoop daemons with the released agent installed only in NameNodes, managed and cleaned by Testcontainers. */
final class HadoopContainers
        implements AutoCloseable
{
    private static final ObjectMapper JSON = new ObjectMapper();
    // Apache's official Hadoop 3.5.0 multiarch index, including Linux arm64 and amd64.
    private static final DockerImageName IMAGE = DockerImageName.parse("ghcr.io/apache/hadoop:3.5.0@sha256:"
            + "389d4c48dcfdc34815d5ad1c4d2aa6ca85714c9c40e34d01ba5acb55b62f2d72");
    private static final String HDFS = "/opt/hadoop/bin/hdfs";
    private static final String CONFIGURATION = "/opt/hadoop/etc/hadoop/";
    private static final String CACHE = "/tmp/grantforge-cache";
    private static final Duration STARTUP = Duration.ofMinutes(5);

    private final Network network = Network.newNetwork();
    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final AtomicInteger files = new AtomicInteger();
    private final SignedGrantForge grantforge;
    private final boolean ha;
    private final Path logs;
    private final GenericContainer<?> namenode;
    private final GenericContainer<?> standby;
    private boolean fallback;

    HadoopContainers(SignedGrantForge grantforge, boolean fallback, boolean ha, String scenario) throws Exception
    {
        this.grantforge = grantforge;
        this.fallback = fallback;
        this.ha = ha;
        logs = Path.of(System.getProperty("grantforge.hdfs.it.logs", "target/hdfs-testcontainers"), scenario);
        Testcontainers.exposeHostPorts(grantforge.uri().getPort());
        Path agent = releaseJar();
        if (ha) {
            GenericContainer<?> journal = daemon("journal", "exec " + HDFS + " journalnode", 8485, 8480);
            configure(journal, "journal", false);
            containers.add(journal);
        }
        String name = ha ? "nn1" : "namenode";
        namenode = daemon(name, "if [ ! -f /tmp/grantforge-name/current/VERSION ]; then " + HDFS
                + " namenode -format -force -nonInteractive; fi\nexec " + HDFS + " namenode", 8020, 9870);
        configure(namenode, name, true);
        namenode.withCopyFileToContainer(MountableFile.forHostPath(agent), "/opt/hadoop/share/hadoop/hdfs/lib/grantforge-agent.jar");
        containers.add(namenode);
        standby = ha ? daemon("nn2", "if [ ! -f /tmp/grantforge-name/current/VERSION ]; then " + HDFS
                + " namenode -bootstrapStandby -force -nonInteractive; fi\nexec " + HDFS + " namenode", 8020, 9870) : namenode;
        if (ha) {
            configure(standby, "nn2", true);
            standby.withCopyFileToContainer(MountableFile.forHostPath(agent), "/opt/hadoop/share/hadoop/hdfs/lib/grantforge-agent.jar");
            containers.add(standby);
        }
        GenericContainer<?> datanode = daemon("datanode", "exec " + HDFS + " datanode", 9866, 9867, 9864);
        configure(datanode, "datanode", false);
        containers.add(datanode);
        try {
            for (GenericContainer<?> container : containers) {
                container.start();
                if (ha && container.equals(namenode)) {
                    // The peer does not exist yet: activate the first node so it can serve the standby's image bootstrap.
                    success(admin("haadmin", "-transitionToActive", "--forceactive", "nn1"));
                }
            }
            Container.ExecResult version = namenode.execInContainer(HDFS, "version");
            success(version);
            assertTrue(version.getStdout().contains("Hadoop 3.5.0"));
            await(() -> admin("dfsadmin", "-report").getStdout().contains("Live datanodes (1)"));
            await(() -> admin("dfsadmin", "-safemode", "get").getStdout().contains("Safe mode is OFF"));
        }
        catch (Exception | AssertionError failed) {
            close();
            throw failed;
        }
    }

    String instance()
    {
        return ha ? "tc-nn1" : "tc-namenode";
    }

    void awaitSnapshot(long version) throws Exception
    {
        await(() -> grantforge.applied(instance(), version) && cached(namenode));
        if (ha) {
            await(() -> grantforge.applied("tc-nn2", version) && cached(standby));
        }
    }

    Container.ExecResult admin(String... arguments) throws IOException, InterruptedException
    {
        return hdfs("hadoop", arguments);
    }

    Container.ExecResult alice(String... arguments) throws IOException, InterruptedException
    {
        String[] command = new String[arguments.length + 1];
        command[0] = "dfs";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        return hdfs("alice", command);
    }

    Container.ExecResult put(String user, String path, String text) throws IOException, InterruptedException
    {
        String source = "/tmp/grantforge-input-" + files.incrementAndGet();
        namenode.copyFileToContainer(Transferable.of(text.getBytes(StandardCharsets.UTF_8)), source);
        return hdfs(user, "dfs", "-put", source, path);
    }

    void append(String path, String text) throws IOException, InterruptedException
    {
        String source = "/tmp/grantforge-input-" + files.incrementAndGet();
        namenode.copyFileToContainer(Transferable.of(text.getBytes(StandardCharsets.UTF_8)), source);
        success(alice("-appendToFile", source, path));
    }

    void restartNameNode() throws Exception
    {
        namenode.getDockerClient().restartContainerCmd(namenode.getContainerId()).withTimeout(10).exec();
        await(() -> admin("dfsadmin", "-report").getStdout().contains("Live datanodes (1)"));
        await(() -> admin("dfsadmin", "-safemode", "get").getStdout().contains("Safe mode is OFF"));
    }

    void nativeFallback(boolean value) throws Exception
    {
        fallback = value;
        namenode.copyFileToContainer(Transferable.of(hdfsConfiguration("namenode", true)), CONFIGURATION + "hdfs-site.xml");
        restartNameNode();
    }

    void failover() throws Exception
    {
        success(admin("haadmin", "-failover", "nn1", "nn2"));
        await(() -> admin("haadmin", "-getServiceState", "nn2").getStdout().strip().equals("active"));
    }

    private Container.ExecResult hdfs(String user, String... arguments) throws IOException, InterruptedException
    {
        String[] command = new String[arguments.length + 3];
        command[0] = "env";
        command[1] = "HADOOP_USER_NAME=" + user;
        command[2] = HDFS;
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        return namenode.execInContainer(command);
    }

    private GenericContainer<?> daemon(String name, String command, Integer... ports)
    {
        return new GenericContainer<>(IMAGE).withNetwork(network).withNetworkAliases(name).withExposedPorts(ports)
                .withEnv("HADOOP_HEAPSIZE_MAX", "256").withEnv("HADOOP_ROOT_LOGGER", "INFO,console")
                .withCreateContainerCmdModifier(container -> container.withHostName(name).withEntrypoint("/bin/bash", "-c"))
                .withCommand(new String[] {"set -e\n" + command})
                .waitingFor(Wait.forListeningPort()).withStartupTimeout(STARTUP);
    }

    private void configure(GenericContainer<?> container, String name, boolean agent)
    {
        container.withCopyToContainer(Transferable.of(xml(Map.of("fs.defaultFS", ha ? "hdfs://grantforge-ha" : "hdfs://namenode:8020",
                "hadoop.security.authentication", "simple", "hadoop.tmp.dir", "/tmp/grantforge-tmp"))), CONFIGURATION + "core-site.xml");
        container.withCopyToContainer(Transferable.of(hdfsConfiguration(name, agent)), CONFIGURATION + "hdfs-site.xml");
        container.withCopyToContainer(Transferable.of(
                "*.sink.jmx.class=org.apache.hadoop.metrics2.sink.JmxSink\n"
                        + // the default period is 10s; the tests read the JMX beans right after their operations
                        "*.period=1\n"), CONFIGURATION + "hadoop-metrics2.properties");
        if (agent) {
            container.withCopyToContainer(Transferable.of(SignedGrantForge.TOKEN), "/tmp/grantforge-agent-token");
            container.withCopyToContainer(Transferable.of(grantforge.publicKey()), "/tmp/grantforge-agent-key");
        }
    }

    /** Reads one JMX bean from the NameNode's web UI, for the metrics assertions of the tests. */
    Map<String, Object> jmx(String bean) throws Exception
    {
        Container.ExecResult result = namenode.execInContainer("curl", "--silent", "--fail",
                "http://localhost:9870/jmx?qry=" + URLEncoder.encode(bean, StandardCharsets.UTF_8));
        success(result);
        JsonNode entry = JSON.readTree(result.getStdout()).path("beans").path(0);
        assertEquals(bean, entry.path("name").asText(), "the queried JMX bean must exist");
        return JSON.convertValue(entry, new TypeReference<Map<String, Object>>() {});
    }

    private String hdfsConfiguration(String name, boolean agent)
    {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("dfs.replication", "1");
        properties.put("dfs.permissions.enabled", "true");
        properties.put("dfs.namenode.acls.enabled", "true");
        properties.put("dfs.namenode.name.dir", "file:///tmp/grantforge-name");
        properties.put("dfs.datanode.data.dir", "file:///tmp/grantforge-data");
        properties.put("dfs.namenode.rpc-bind-host", "0.0.0.0");
        properties.put("dfs.namenode.http-bind-host", "0.0.0.0");
        properties.put("dfs.datanode.hostname", "datanode");
        properties.put("dfs.client.use.datanode.hostname", "true");
        properties.put("dfs.datanode.use.datanode.hostname", "true");
        properties.put("dfs.heartbeat.interval", "1");
        properties.put("dfs.namenode.safemode.extension", "0");
        properties.put("dfs.blocksize", "1048576");
        properties.put("dfs.namenode.handler.count", "2");
        properties.put("dfs.datanode.handler.count", "2");
        properties.put("ipc.client.connect.timeout", "1000");
        properties.put("ipc.client.connect.max.retries", "2");
        if (ha) {
            properties.put("dfs.nameservices", "grantforge-ha");
            properties.put("dfs.ha.namenodes.grantforge-ha", "nn1,nn2");
            properties.put("dfs.namenode.rpc-address.grantforge-ha.nn1", "nn1:8020");
            properties.put("dfs.namenode.rpc-address.grantforge-ha.nn2", "nn2:8020");
            properties.put("dfs.namenode.http-address.grantforge-ha.nn1", "nn1:9870");
            properties.put("dfs.namenode.http-address.grantforge-ha.nn2", "nn2:9870");
            properties.put("dfs.namenode.shared.edits.dir", "qjournal://journal:8485/grantforge");
            properties.put("dfs.client.failover.proxy.provider.grantforge-ha",
                    "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");
            properties.put("dfs.ha.automatic-failover.enabled", "false");
            properties.put("dfs.ha.fencing.methods", "shell(/bin/true)");
            properties.put("dfs.ha.tail-edits.period", "1");
            properties.put("dfs.client.failover.sleep.base.millis", "100");
            properties.put("dfs.client.failover.sleep.max.millis", "500");
            if (name.startsWith("nn")) {
                properties.put("dfs.ha.namenode.id", name);
            }
            properties.put("dfs.journalnode.edits.dir", "/tmp/grantforge-journal");
            properties.put("dfs.journalnode.rpc-address", "0.0.0.0:8485");
            properties.put("dfs.journalnode.http-address", "0.0.0.0:8480");
        }
        else {
            properties.put("dfs.namenode.rpc-address", "namenode:8020");
            properties.put("dfs.namenode.http-address", "namenode:9870");
        }
        if (agent) {
            properties.put("dfs.namenode.inode.attributes.provider.class", "org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider");
            properties.put("grantforge.hdfs.server.url", "http://host.testcontainers.internal:" + grantforge.uri().getPort() + "/");
            properties.put("grantforge.hdfs.token.file", "/tmp/grantforge-agent-token");
            properties.put("grantforge.hdfs.signing.key.file", "/tmp/grantforge-agent-key");
            properties.put("grantforge.hdfs.instance", "tc-" + name);
            properties.put("grantforge.hdfs.cache.dir", CACHE);
            properties.put("grantforge.hdfs.native.fallback", Boolean.toString(fallback));
            properties.put("grantforge.hdfs.connect.timeout.ms", "1000");
            properties.put("grantforge.hdfs.read.timeout.ms", "1000");
            properties.put("grantforge.hdfs.refresh.interval.ms", "1000");
            properties.put("grantforge.hdfs.audit.flush.interval.ms", "100");
        }
        return xml(properties);
    }

    private static String xml(Map<String, String> properties)
    {
        StringBuilder result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<configuration>\n");
        properties.forEach((key, value) -> result.append("  <property><name>").append(key).append("</name><value>")
                .append(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                .append("</value></property>\n"));
        return result.append("</configuration>\n").toString();
    }

    private static Path releaseJar() throws IOException
    {
        String configured = System.getProperty("grantforge.hdfs.it.agent.jar");
        Path agent = configured == null ? packagedJar() : Path.of(configured);
        assertTrue(Files.isRegularFile(agent), "the cluster must use the packaged agent jar");
        try (JarFile jar = new JarFile(agent.toFile())) {
            assertNotNull(jar.getEntry("org/devlive/grantforge/hdfs/agent/HdfsAuthorizationProvider.class"));
            assertNotNull(jar.getEntry("org/devlive/grantforge/hdfs/agent/internal/jackson/databind/ObjectMapper.class"));
            assertNotNull(jar.getEntry("org/devlive/grantforge/hdfs/agent/internal/bouncycastle/crypto/signers/Ed25519Signer.class"));
        }
        return agent;
    }

    /** Finds the shaded jar the package phase left in target/; the NameNode mounts the released artifact, not classes. */
    private static Path packagedJar() throws IOException
    {
        Properties build = new Properties();
        Path descriptor = Path.of("target", "maven-archiver", "pom.properties");
        if (Files.isRegularFile(descriptor)) {
            try (InputStream values = Files.newInputStream(descriptor)) {
                build.load(values);
            }
        }
        String artifactId = build.getProperty("artifactId");
        String version = build.getProperty("version");
        assertTrue(artifactId != null && version != null, "the package phase must leave the build metadata in target/");
        Path agent = Path.of("target", artifactId + "-" + version + ".jar");
        assertTrue(Files.isRegularFile(agent), "the verify phase must package the agent jar " + agent.getFileName() + " into target/");
        return agent;
    }

    private static boolean cached(GenericContainer<?> container) throws IOException, InterruptedException
    {
        return container.execInContainer("test", "-s", CACHE + "/snapshot.properties").getExitCode() == 0;
    }

    static void success(Container.ExecResult result)
    {
        assertEquals(0, result.getExitCode(), result.getStdout() + result.getStderr());
    }

    static void await(Check ready) throws Exception
    {
        long deadline = System.nanoTime() + STARTUP.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (ready.ready()) {
                    return;
                }
            }
            catch (IOException transientFailure) {
                // A restarting NameNode may close an exec connection before the next bounded retry.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("the Hadoop containers did not reach the expected state within " + STARTUP);
    }

    @Override
    public void close()
    {
        for (int index = containers.size() - 1; index >= 0; index--) {
            GenericContainer<?> container = containers.get(index);
            try {
                Files.createDirectories(logs);
                String name = container.getNetworkAliases().stream().filter(alias -> !alias.startsWith("tc-")).findFirst().orElse("daemon-" + index);
                Files.writeString(logs.resolve(name + ".log"), container.getLogs(), StandardCharsets.UTF_8);
            }
            catch (IOException | RuntimeException unavailable) {
                System.err.println("Could not save Hadoop container logs: " + unavailable);
            }
            container.close();
        }
        network.close();
    }

    @FunctionalInterface
    interface Check
    {
        boolean ready() throws Exception;
    }
}
