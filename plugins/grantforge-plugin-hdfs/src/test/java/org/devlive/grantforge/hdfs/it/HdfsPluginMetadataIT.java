// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.devlive.grantforge.hdfs.HdfsProvider;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The real Hadoop daemon, with no agent installed, exercises client-to-server metadata protocol compatibility. */
@Timeout(1200)
class HdfsPluginMetadataIT
{
    private static final String HDFS = "/opt/hadoop/bin/hdfs";

    @Test
    void browsesRealNameNodeDirectoriesOverSupportedMetadataTransports() throws Exception
    {
        HadoopRuntime runtime = HadoopRuntime.configured();
        try (GenericContainer<?> namenode = runtime.container().withExposedPorts(8020, runtime.httpPort())
                .withEnv("HADOOP_HEAPSIZE_MAX", "256").withEnv("HADOOP_HEAPSIZE", "256")
                .withEnv("HADOOP_ROOT_LOGGER", "INFO,console")
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/bash", "-c"))
                .withCommand(new String[] {"set -e\n" + HDFS + " namenode -format -force -nonInteractive\nexec " + HDFS + " namenode"})
                .withCopyToContainer(Transferable.of(xml(Map.of("fs.defaultFS", "hdfs://localhost:8020",
                        "hadoop.security.authentication", "simple", "hadoop.tmp.dir", "/tmp/grantforge-metadata"))),
                        "/opt/hadoop/etc/hadoop/core-site.xml")
                .withCopyToContainer(Transferable.of(xml(configuration(runtime))), "/opt/hadoop/etc/hadoop/hdfs-site.xml")
                .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(5))) {
            try {
                namenode.start();
                Container.ExecResult version = namenode.execInContainer(HDFS, "version");
                success(version);
                assertThat(version.getStdout()).contains("Hadoop " + runtime.version());
                success(namenode.execInContainer(HDFS, "dfsadmin", "-safemode", "leave"));
                success(namenode.execInContainer(HDFS, "dfs", "-mkdir", "-p", "/metadata/alpha", "/metadata/beta"));
                success(namenode.execInContainer(HDFS, "dfs", "-touchz", "/metadata/notes.txt"));
                HdfsProvider provider = new HdfsProvider();
                List<String> addresses = new ArrayList<>();
                addresses.add("webhdfs://" + namenode.getHost() + ":" + namenode.getMappedPort(runtime.httpPort()));
                if (runtime.version().startsWith("3.")) {
                    addresses.add("hdfs://" + namenode.getHost() + ":" + namenode.getMappedPort(8020));
                }
                for (String address : addresses) {
                    ServiceConfig service = new ServiceConfig("metadata-cluster", Map.of("fs.default.name", address, "username", "hadoop",
                            "lookup.path", "/metadata", "lookup.max.entries", "100"));
                    assertThat(provider.testConnection(service)).isEqualTo(ConnectionResult.succeeded());
                    assertThat(provider.lookup(new LookupRequest(service, "path", "/metadata/", Map.of(), 10)))
                            .containsExactly("/metadata/alpha", "/metadata/beta", "/metadata/notes.txt");
                    assertThat(provider.lookup(new LookupRequest(service, "path", "/metadata/a", Map.of(), 1)))
                            .containsExactly("/metadata/alpha");
                }
            }
            finally {
                saveLogs(namenode, runtime);
            }
        }
    }

    private static void saveLogs(GenericContainer<?> namenode, HadoopRuntime runtime)
    {
        try {
            Path logs = Path.of(System.getProperty("grantforge.hdfs.it.logs", "target/hdfs-testcontainers"), runtime.version());
            Files.createDirectories(logs);
            Files.writeString(logs.resolve("namenode.log"), namenode.getLogs(), StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException unavailable) {
            // Preserve the original startup or assertion failure even when Docker cannot return its logs.
            System.err.println("Could not save plugin metadata NameNode logs: " + unavailable);
        }
    }

    private static Map<String, String> configuration(HadoopRuntime runtime)
    {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("dfs.namenode.name.dir", "file:///tmp/grantforge-metadata-name");
        values.put("dfs.namenode.rpc-address", "0.0.0.0:8020");
        values.put("dfs.namenode.rpc-bind-host", "0.0.0.0");
        values.put("dfs.namenode.http-address", "0.0.0.0:" + runtime.httpPort());
        values.put("dfs.namenode.http-bind-host", "0.0.0.0");
        values.put("dfs.namenode.safemode.extension", "0");
        values.put("dfs.namenode.safemode.min.datanodes", "0");
        values.put("dfs.permissions.enabled", "true");
        values.put("dfs.namenode.handler.count", "2");
        return values;
    }

    private static String xml(Map<String, String> values)
    {
        StringBuilder xml = new StringBuilder("<configuration>");
        values.forEach((name, value) -> xml.append("<property><name>").append(name).append("</name><value>")
                .append(value).append("</value></property>"));
        return xml.append("</configuration>").toString();
    }

    private static void success(Container.ExecResult result)
    {
        assertThat(result.getExitCode()).withFailMessage(result.getStdout() + result.getStderr()).isZero();
    }
}
