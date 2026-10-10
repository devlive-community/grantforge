// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.DataInputStream;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Separate Hadoop daemons with the released agent installed only in NameNodes, managed and cleaned by Testcontainers. */
final class HadoopContainers
        implements AutoCloseable
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HDFS = "/opt/hadoop/bin/hdfs";
    private static final String CONFIGURATION = "/opt/hadoop/etc/hadoop/";
    private static final String CACHE = "/tmp/grantforge-cache";
    private static final Duration STARTUP = Duration.ofMinutes(5);
    private static final String SECURITY = "/opt/hadoop/etc/security";
    private static final int HTTPS = 9871;
    private static final int DATANODE_HTTPS = 9865;
    private static final DockerImageName ZOOKEEPER = DockerImageName.parse("zookeeper:3.9.3");
    private static final List<String> JOURNALS = List.of("journal1", "journal2", "journal3");

    private final Network network = Network.newNetwork();
    private final HadoopRuntime runtime = HadoopRuntime.configured();
    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final AtomicInteger files = new AtomicInteger();
    private final SignedGrantForge grantforge;
    private final boolean ha;
    private final boolean automatic;
    private final Path logs;
    private final GenericContainer<?> namenode;
    private final GenericContainer<?> standby;
    private final @Nullable KerberosSetup kerberos;
    private final Map<String, GenericContainer<?>> daemons = new LinkedHashMap<>();
    private GenericContainer<?> client;
    private boolean fallback;

    HadoopContainers(SignedGrantForge grantforge, boolean fallback, boolean ha, String scenario) throws Exception
    {
        this(grantforge, fallback, ha, false, scenario, null);
    }

    /**
     * Automatic HA: two NameNodes with the agent, each with its ZKFC in the same container so that losing the
     * container loses both, one ZooKeeper, a quorum of three JournalNodes and one DataNode. Commands run on the
     * DataNode, which stays up while NameNodes come and go.
     */
    static HadoopContainers automaticHa(SignedGrantForge grantforge, String scenario) throws Exception
    {
        return new HadoopContainers(grantforge, false, true, true, scenario, null);
    }

    /**
     * A Kerberos cluster: one NameNode with the agent and one DataNode, signed in from keytabs, with SASL-protected
     * data transfer and HTTPS only, as Hadoop requires of a secure DataNode without privileged ports.
     */
    static HadoopContainers secure(SignedGrantForge grantforge, String scenario, KerberosSetup kerberos) throws Exception
    {
        return new HadoopContainers(grantforge, false, false, false, scenario, kerberos);
    }

    private HadoopContainers(SignedGrantForge grantforge, boolean fallback, boolean ha, boolean automatic, String scenario,
                             @Nullable KerberosSetup kerberos)
            throws Exception
    {
        this.grantforge = grantforge;
        this.fallback = fallback;
        this.ha = ha;
        this.automatic = automatic;
        this.kerberos = kerberos;
        logs = Path.of(System.getProperty("grantforge.hdfs.it.logs", "target/hdfs-testcontainers"), scenario);
        Testcontainers.exposeHostPorts(grantforge.uri().getPort());
        if (kerberos != null) {
            Testcontainers.exposeHostPorts(kerberos.kdcPort());
        }
        Path agent = releaseJar();
        if (automatic) {
            GenericContainer<?> zookeeper = new GenericContainer<>(ZOOKEEPER).withNetwork(network).withNetworkAliases("zookeeper")
                    .withExposedPorts(2181).waitingFor(Wait.forListeningPort()).withStartupTimeout(STARTUP);
            containers.add(zookeeper);
        }
        List<String> journals = automatic ? JOURNALS : List.of("journal");
        for (String journalName : ha ? journals : List.<String>of()) {
            GenericContainer<?> journal = daemon(journalName, "exec " + HDFS + " journalnode", 8485, 8480);
            configure(journal, journalName, false);
            containers.add(journal);
        }
        String name = ha ? "nn1" : "namenode";
        // With automatic failover each NameNode has its ZKFC; the first one also creates the election's znode.
        String zkfc = automatic ? HDFS + " zkfc &\n" : "";
        namenode = daemon(name, "if [ ! -f /tmp/grantforge-name/current/VERSION ]; then " + HDFS
                + " namenode -format -force -nonInteractive" + (automatic ? "; " + HDFS + " zkfc -formatZK -force -nonInteractive" : "")
                + "; fi\n" + zkfc + "exec " + HDFS + " namenode", 8020, kerberos == null ? runtime.httpPort() : HTTPS);
        configure(namenode, name, true);
        namenode.withCopyFileToContainer(MountableFile.forHostPath(agent), "/opt/hadoop/share/hadoop/hdfs/lib/grantforge-agent.jar");
        containers.add(namenode);
        standby = ha ? daemon("nn2", "if [ ! -f /tmp/grantforge-name/current/VERSION ]; then " + HDFS
                + " namenode -bootstrapStandby -force -nonInteractive; fi\n" + zkfc + "exec " + HDFS + " namenode", 8020, runtime.httpPort()) : namenode;
        if (ha) {
            configure(standby, "nn2", true);
            standby.withCopyFileToContainer(MountableFile.forHostPath(agent), "/opt/hadoop/share/hadoop/hdfs/lib/grantforge-agent.jar");
            containers.add(standby);
        }
        Integer[] datanodePorts = runtime.datanodePorts();
        // HTTPS only opens no HTTP port to wait for.
        GenericContainer<?> datanode = daemon("datanode", "exec " + HDFS + " datanode", kerberos == null ? datanodePorts
                : new Integer[] {datanodePorts[0], datanodePorts[1], DATANODE_HTTPS});
        configure(datanode, "datanode", false);
        containers.add(datanode);
        client = namenode;
        for (GenericContainer<?> container : containers) {
            container.getNetworkAliases().stream().filter(alias -> !alias.startsWith("tc-")).findFirst()
                    .ifPresent(alias -> daemons.put(alias, container));
        }
        try {
            for (GenericContainer<?> container : containers) {
                container.start();
                if (automatic && container.equals(namenode)) {
                    // Alone in the election, the first node's ZKFC makes it active, so it can serve the standby's bootstrap.
                    awaitActive("nn1");
                }
                else if (ha && container.equals(namenode)) {
                    // The peer does not exist yet: activate the first node so it can serve the standby's image bootstrap.
                    success(admin("haadmin", "-transitionToActive", "--forceactive", "nn1"));
                }
            }
            if (automatic) {
                client = datanode;
            }
            Container.ExecResult version = namenode.execInContainer(HDFS, "version");
            success(version);
            assertTrue(version.getStdout().contains("Hadoop " + runtime.version()), "the daemon must run the exact matrix version");
            Container.ExecResult java = namenode.execInContainer("java", "-version");
            success(java);
            String expectedJava = runtime.javaMajor() == 8 ? "\"1.8." : "\"" + runtime.javaMajor() + ".";
            assertTrue((java.getStdout() + java.getStderr()).contains(expectedJava), "the daemon must use the matrix JVM: " + java.getStderr());
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

    Container.ExecResult user(String user, String... arguments) throws IOException, InterruptedException
    {
        return hdfs(user, arguments);
    }

    Container.ExecResult put(String user, String path, String text) throws IOException, InterruptedException
    {
        String source = "/tmp/grantforge-input-" + files.incrementAndGet();
        client.copyFileToContainer(Transferable.of(text.getBytes(StandardCharsets.UTF_8)), source);
        return hdfs(user, "dfs", "-put", source, path);
    }

    void append(String path, String text) throws IOException, InterruptedException
    {
        String source = "/tmp/grantforge-input-" + files.incrementAndGet();
        client.copyFileToContainer(Transferable.of(text.getBytes(StandardCharsets.UTF_8)), source);
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

    /** Kills a daemon's container outright, as a lost host would go, keeping its disk for {@link #start(String)}. */
    void kill(String daemon)
    {
        GenericContainer<?> container = container(daemon);
        container.getDockerClient().killContainerCmd(container.getContainerId()).exec();
    }

    /** Starts a killed daemon's container again, on the state it left behind. */
    void start(String daemon)
    {
        GenericContainer<?> container = container(daemon);
        container.getDockerClient().startContainerCmd(container.getContainerId()).exec();
    }

    /** The output a daemon has written so far, kept after its container stopped. */
    String logs(String daemon)
    {
        return container(daemon).getLogs();
    }

    void awaitActive(String nameNode) throws Exception
    {
        awaitState(nameNode, "active");
    }

    void awaitState(String nameNode, String state) throws Exception
    {
        await(() -> admin("haadmin", "-getServiceState", nameNode).getStdout().strip().equals(state));
    }

    /** Hands the active role over through the ZKFCs, which is how an administrator moves it under automatic failover. */
    void failover(String from, String to) throws Exception
    {
        success(admin("haadmin", "-failover", from, to));
        awaitActive(to);
    }

    private GenericContainer<?> container(String name)
    {
        GenericContainer<?> container = daemons.get(name);
        assertNotNull(container, "no daemon " + name + " in this cluster");
        return container;
    }

    /**
     * Runs a command as a user: named through HADOOP_USER_NAME, or in a Kerberos cluster with the user's ticket
     * cache, where the superuser is nn, the NameNode's own short name.
     */
    private Container.ExecResult hdfs(String user, String... arguments) throws IOException, InterruptedException
    {
        if (kerberos == null) {
            return run(List.of("env", "HADOOP_USER_NAME=" + user), arguments);
        }
        String name = "hadoop".equals(user) ? "nn" : user;
        return run(List.of("env", "KRB5CCNAME=" + SECURITY + "/krb5cc_" + name), arguments);
    }

    /** Runs a command with no Kerberos ticket at all, as an unauthenticated client would. */
    Container.ExecResult withoutTicket(String... arguments) throws IOException, InterruptedException
    {
        return run(List.of("env", "KRB5CCNAME=/tmp/krb5cc_nobody", "HADOOP_USER_NAME=alice"), arguments);
    }

    private Container.ExecResult run(List<String> environment, String... arguments) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>(environment);
        command.add(HDFS);
        command.addAll(List.of(arguments));
        return client.execInContainer(command.toArray(new String[0]));
    }

    private GenericContainer<?> daemon(String name, String command, Integer... ports)
    {
        return runtime.container().withNetwork(network).withNetworkAliases(name).withExposedPorts(ports)
                .withEnv("HADOOP_HEAPSIZE_MAX", "256").withEnv("HADOOP_HEAPSIZE", "256").withEnv("HADOOP_ROOT_LOGGER", "INFO,console")
                .withCreateContainerCmdModifier(container -> container.withHostName(name).withEntrypoint("/bin/bash", "-c"))
                .withCommand(new String[] {"set -e\n" + command})
                .waitingFor(Wait.forListeningPort()).withStartupTimeout(STARTUP);
    }

    private void configure(GenericContainer<?> container, String name, boolean agent)
    {
        Map<String, String> core = new LinkedHashMap<>();
        core.put("fs.defaultFS", ha ? "hdfs://grantforge-ha" : "hdfs://namenode:8020");
        core.put("hadoop.security.authentication", kerberos == null ? "simple" : "kerberos");
        core.put("hadoop.tmp.dir", "/tmp/grantforge-tmp");
        if (automatic) {
            core.put("ha.zookeeper.quorum", "zookeeper:2181");
            // A lost NameNode's ZKFC session ends this soon after, and the other one takes over.
            core.put("ha.zookeeper.session-timeout.ms", "3000");
        }
        if (kerberos != null) {
            core.put("hadoop.security.authorization", "true");
            core.put("hadoop.rpc.protection", "authentication");
            // The web servers' filter takes its kind from here, simple and anonymous unless told: SPNEGO.
            core.put("hadoop.http.authentication.type", "kerberos");
            core.put("hadoop.http.authentication.kerberos.principal", "*");
            core.put("hadoop.http.authentication.kerberos.keytab", SECURITY + "/http-service.keytab");
            core.put("hadoop.http.authentication.simple.anonymous.allowed", "false");
            secure(container, kerberos);
        }
        container.withCopyToContainer(Transferable.of(xml(core)), CONFIGURATION + "core-site.xml");
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

    /** Gives a daemon of a Kerberos cluster the KDC, its keytabs and the TLS stores of HTTPS only. */
    private static void secure(GenericContainer<?> container, KerberosSetup kerberos)
    {
        container.withCopyToContainer(Transferable.of("""
                [libdefaults]
                  default_realm = %s
                  udp_preference_limit = 1
                  rdns = false
                  dns_canonicalize_hostname = false
                  dns_lookup_kdc = false
                  dns_lookup_realm = false
                [realms]
                  %s = {
                    kdc = host.testcontainers.internal:%d
                  }
                """.formatted(kerberos.realm(), kerberos.realm(), kerberos.kdcPort())), "/etc/krb5.conf");
        try (Stream<Path> files = Files.list(kerberos.security())) {
            for (Path file : files.toList()) {
                container.withCopyToContainer(Transferable.of(Files.readAllBytes(file), 0644), SECURITY + "/" + file.getFileName());
            }
        }
        catch (IOException unreadable) {
            throw new IllegalStateException("cannot read the cluster's keytabs and stores", unreadable);
        }
        container.withCopyToContainer(Transferable.of(xml(Map.of("ssl.server.keystore.location", SECURITY + "/keystore.p12",
                "ssl.server.keystore.type", "pkcs12", "ssl.server.keystore.password", KerberosSetup.STORE_PASSWORD,
                "ssl.server.keystore.keypassword", KerberosSetup.STORE_PASSWORD))), CONFIGURATION + "ssl-server.xml");
        container.withCopyToContainer(Transferable.of(xml(Map.of("ssl.client.truststore.location", SECURITY + "/truststore.jks",
                "ssl.client.truststore.type", "jks", "ssl.client.truststore.password", KerberosSetup.STORE_PASSWORD))),
                CONFIGURATION + "ssl-client.xml");
    }

    /** Reads one JMX bean from the NameNode's web UI, for the metrics assertions of the tests. */
    Map<String, Object> jmx(String bean) throws Exception
    {
        Container.ExecResult result = namenode.execInContainer("curl", "--silent", "--fail",
                "http://localhost:" + runtime.httpPort() + "/jmx?qry=" + URLEncoder.encode(bean, StandardCharsets.UTF_8));
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
        Integer[] datanodePorts = runtime.datanodePorts();
        properties.put("dfs.datanode.address", "0.0.0.0:" + datanodePorts[0]);
        properties.put("dfs.datanode.ipc.address", "0.0.0.0:" + datanodePorts[1]);
        properties.put("dfs.datanode.http.address", "0.0.0.0:" + datanodePorts[2]);
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
            properties.put("dfs.namenode.http-address.grantforge-ha.nn1", "nn1:" + runtime.httpPort());
            properties.put("dfs.namenode.http-address.grantforge-ha.nn2", "nn2:" + runtime.httpPort());
            properties.put("dfs.namenode.shared.edits.dir", automatic
                    ? "qjournal://" + String.join(";", JOURNALS.stream().map(journal -> journal + ":8485").toList()) + "/grantforge"
                    : "qjournal://journal:8485/grantforge");
            properties.put("dfs.client.failover.proxy.provider.grantforge-ha",
                    "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");
            properties.put("dfs.ha.automatic-failover.enabled", Boolean.toString(automatic));
            if (automatic) {
                // Without a quorum the active NameNode gives up writing its edits this soon, and stops.
                properties.put("dfs.qjournal.write-txns.timeout.ms", "5000");
            }
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
            properties.put("dfs.namenode.http-address", "namenode:" + runtime.httpPort());
        }
        if (kerberos != null) {
            String realm = kerberos.realm();
            properties.put("dfs.namenode.kerberos.principal", "nn/namenode@" + realm);
            properties.put("dfs.namenode.keytab.file", SECURITY + "/nn-service.keytab");
            properties.put("dfs.datanode.kerberos.principal", "dn/datanode@" + realm);
            properties.put("dfs.datanode.keytab.file", SECURITY + "/dn-service.keytab");
            // Every HTTP principal in the keytab, so each daemon finds its own.
            properties.put("dfs.web.authentication.kerberos.principal", "*");
            properties.put("dfs.web.authentication.kerberos.keytab", SECURITY + "/http-service.keytab");
            properties.put("dfs.block.access.token.enable", "true");
            // A DataNode on unprivileged ports is secure only with SASL on its data transfer and HTTPS only.
            properties.put("dfs.data.transfer.protection", "authentication");
            properties.put("dfs.http.policy", "HTTPS_ONLY");
            properties.put("dfs.namenode.https-address", "namenode:" + HTTPS);
            properties.put("dfs.namenode.https-bind-host", "0.0.0.0");
            properties.put("dfs.datanode.https.address", "0.0.0.0:" + DATANODE_HTTPS);
            // Reaching the KDC on the host gives the DataNode a second address, on Docker's default bridge, which may
            // be the one it registers from and has no name. Kerberos already proves it is dn/datanode; clients reach
            // it by its hostname.
            properties.put("dfs.namenode.datanode.registration.ip-hostname-check", "false");
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

    private Path releaseJar() throws IOException
    {
        String configured = System.getProperty("grantforge.hdfs.it.agent.jar");
        Path agent = configured == null ? packagedJar() : Path.of(configured);
        assertTrue(Files.isRegularFile(agent), "the cluster must use the packaged agent jar");
        try (JarFile jar = new JarFile(agent.toFile())) {
            int adapterBytecode = runtime.version().startsWith("3.5.") ? 61 : 52;
            packagedClass(jar, "HdfsAuthorizationProvider", adapterBytecode);
            packagedClass(jar, "HdfsAccessControlEnforcer", adapterBytecode);
            // The numbered jar must carry its binary native-module dependency, without compiling its shared sources again.
            for (String shared : List.of("HdfsNativeAuthorizationProvider", "HdfsAgentCompatibility", "HdfsNativeEnforcer",
                    "HdfsNativeNode", "HdfsAgentMetricsWrapper")) {
                packagedClass(jar, shared, 52);
            }
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/apache/hadoop/")
                            && entry.getName().endsWith(".class")),
                    "the agent must use the container's Hadoop classes rather than shade a different native runtime");
            assertNotNull(jar.getManifest(), "the release jar must retain its manifest");
            assertNotNull(jar.getEntry("org/devlive/grantforge/hdfs/agent/internal/jackson/databind/ObjectMapper.class"));
            assertNotNull(jar.getEntry("org/devlive/grantforge/hdfs/agent/internal/bouncycastle/crypto/signers/Ed25519Signer.class"));
            String resource = "META-INF/grantforge/hdfs-agent-version.properties";
            assertEquals(1L, jar.stream().filter(entry -> entry.getName().equals(resource)).count(),
                    "only the numbered adapter may contribute runtime version metadata");
            Properties metadata = new Properties();
            try (InputStream values = jar.getInputStream(jar.getJarEntry(resource))) {
                metadata.load(values);
            }
            String line = runtime.version().substring(0, runtime.version().lastIndexOf('.'));
            assertEquals(runtime.version(), metadata.getProperty("hadoop.version"), "the copied artifact must match the exact matrix target");
            assertEquals(line, metadata.getProperty("hadoop.line"), "the copied artifact must carry its own adapter line");
            String family = switch (line) {
                case "2.7", "2.10", "3.2" -> "parameters";
                case "3.3" -> "context";
                case "3.4", "3.5" -> "superuser";
                default -> throw new IllegalArgumentException("no native SPI family for " + line);
            };
            assertEquals(family, metadata.getProperty("spi.family"), "shared native classes must not supply another adapter's metadata");
            assertEquals(runtime.version().startsWith("3.5.") ? "17" : "8", metadata.getProperty("java.minimum"),
                    "the artifact bytecode baseline must match its Hadoop generation");
        }
        return agent;
    }

    private static void packagedClass(JarFile jar, String name, int bytecode) throws IOException
    {
        String path = "org/devlive/grantforge/hdfs/agent/" + name + ".class";
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, "the release jar must include " + name);
        assertEquals(1L, jar.stream().filter(value -> value.getName().equals(path)).count(),
                "the release jar must contain one binary implementation of " + name);
        try (DataInputStream contents = new DataInputStream(jar.getInputStream(entry))) {
            assertEquals(0xCAFEBABE, contents.readInt(), "the packaged entry must be a JVM class");
            contents.readUnsignedShort(); // minor class-file version
            assertEquals(bytecode, contents.readUnsignedShort(), name + " must retain its module's Java bytecode baseline");
        }
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
