// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.SafeModeAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.devlive.grantforge.hdfs.HdfsProvider;
import org.devlive.grantforge.hdfs.TestTls;
import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A Kerberos NameNode as a secure cluster runs it, for the plugin's metadata calls: a real KDC, RPC signed in with
 * SASL/GSSAPI, and WebHDFS served only over TLS with SPNEGO. Lookups need no DataNode, so there is none.
 *
 * <p>The KDC runs in this JVM and reaches the container through Testcontainers' host port. This JVM reaches the
 * NameNode through mapped ports on localhost, a name its principals do not carry: RPC therefore names the NameNode's
 * principal literally, and the NameNode accepts SPNEGO for HTTP/localhost as well as HTTP/namenode.
 */
@Timeout(1200)
class HdfsSecureMetadataIT
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String HDFS = "/opt/hadoop/bin/hdfs";
    private static final String SECURITY = "/opt/hadoop/etc/security";
    private static final String NAMENODE_PRINCIPAL = "nn/namenode@" + REALM;
    private static final String ADMIN = "nn@" + REALM;
    private static final String LOOKUP = "grantforge@" + REALM;
    private static final String STRANGER = "mallory@" + REALM;
    private static final String STRANGER_PASSWORD = "mallory-secret";
    private static final int RPC = 8020;
    private static final int HTTPS = 9871;

    @TempDir
    java.nio.file.Path work;

    @Test
    void looksUpAKerberosClusterOverRpcAndSwebhdfsWithoutFallingBackToSimple() throws Exception
    {
        HadoopRuntime runtime = HadoopRuntime.configured();
        // The baseline of secure mode; further versions are registered one by one once this one passes.
        assumeTrue("3.5.0".equals(runtime.version()), "secure HDFS is certified on Hadoop 3.5.0 first");

        int kdcPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            kdcPort = socket.getLocalPort();
        }
        SimpleKdcServer kdc = new SimpleKdcServer();
        kdc.setWorkDir(work.toFile());
        kdc.setKdcRealm(REALM);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(kdcPort);
        kdc.init();
        kdc.start();
        try {
            File nnKeytab = keytab(kdc, work.resolve("nn.keytab").toFile(), NAMENODE_PRINCIPAL);
            File httpKeytab = keytab(kdc, work.resolve("http.keytab").toFile(), "HTTP/namenode@" + REALM, "HTTP/localhost@" + REALM);
            // Signed in from its keytab the NameNode runs as nn, its superuser: an administrator is any principal of that
            // short name. This needs no operating system group of the image.
            File adminKeytab = keytab(kdc, work.resolve("admin.keytab").toFile(), ADMIN);
            File lookupKeytab = keytab(kdc, work.resolve("lookup.keytab").toFile(), LOOKUP);
            kdc.createPrincipal(STRANGER, STRANGER_PASSWORD);
            // This JVM is the test class's own (failsafe does not reuse forks), so the setting cannot leak.
            System.setProperty("java.security.krb5.conf", work.resolve("krb5.conf").toString());
            Testcontainers.exposeHostPorts(kdcPort);
            TestTls tls = TestTls.create(Files.createDirectories(work.resolve("tls")));

            try (GenericContainer<?> namenode = namenode(runtime, kdcPort, nnKeytab, httpKeytab, tls)) {
                try {
                    namenode.start();
                    prepare("hdfs://localhost:" + namenode.getMappedPort(RPC), adminKeytab);
                    String rpc = "hdfs://localhost:" + namenode.getMappedPort(RPC);
                    String swebhdfs = "swebhdfs://localhost:" + namenode.getMappedPort(HTTPS);
                    HdfsProvider provider = new HdfsProvider();

                    // RPC signed in with a keytab, and the paths browsed as that principal.
                    ServiceConfig keytab = secure(rpc, "username", LOOKUP, "keytab", lookupKeytab.getPath());
                    assertThat(provider.testConnection(keytab)).isEqualTo(ConnectionResult.succeeded());
                    assertThat(provider.lookup(new LookupRequest(keytab, "path", "/secure/", Map.of(), 10)))
                            .containsExactly("/secure/alpha", "/secure/beta", "/secure/private", "/secure/notes.txt");
                    assertThat(provider.browse(new BrowseRequest(keytab, "path", "/secure", null, 10)).entries())
                            .extracting(BrowseEntry::name).containsExactly("alpha", "beta", "notes.txt", "private");

                    // WebHDFS only over TLS, with SPNEGO, trusting the cluster's certificate through the service's truststore.
                    ServiceConfig tlsKeytab = secure(swebhdfs, "username", LOOKUP, "keytab", lookupKeytab.getPath(),
                            "ssl.client.truststore.location", tls.trustStore().toString(),
                            "ssl.client.truststore.password", TestTls.TRUST_PASSWORD);
                    assertThat(provider.testConnection(tlsKeytab)).isEqualTo(ConnectionResult.succeeded());
                    assertThat(provider.lookup(new LookupRequest(tlsKeytab, "path", "/secure/a", Map.of(), 10)))
                            .containsExactly("/secure/alpha");
                    reason(() -> provider.lookup(new LookupRequest(secure(swebhdfs, "username", LOOKUP, "keytab",
                            lookupKeytab.getPath()), "path", "/secure/", Map.of(), 10)), LookupException.Reason.UNREACHABLE);

                    // A password sign-in is the principal's own: the NameNode's permissions still apply to it.
                    ServiceConfig stranger = secure(rpc, "username", STRANGER, "password", STRANGER_PASSWORD);
                    assertThat(provider.lookup(new LookupRequest(stranger, "path", "/secure/a", Map.of(), 10)))
                            .containsExactly("/secure/alpha");
                    reason(() -> provider.lookup(new LookupRequest(stranger, "path", "/secure/private/", Map.of(), 10)),
                            LookupException.Reason.ACCESS_DENIED);

                    // Wrong credentials fail as such, and never turn into a simple sign-in the cluster would refuse anyway.
                    reason(() -> provider.lookup(new LookupRequest(secure(rpc, "username", STRANGER, "password", "not the secret"),
                            "path", "/secure/", Map.of(), 10)), LookupException.Reason.AUTHENTICATION_FAILED);
                    reason(() -> provider.lookup(new LookupRequest(secure(rpc, "username", STRANGER, "keytab", lookupKeytab.getPath()),
                            "path", "/secure/", Map.of(), 10)), LookupException.Reason.AUTHENTICATION_FAILED);
                    ServiceConfig simple = new ServiceConfig("simple", Map.of("fs.default.name", rpc, "username", "hadoop"));
                    ConnectionResult refused = provider.testConnection(simple);
                    assertThat(refused.status()).isEqualTo(ConnectionResult.Status.FAILED);
                    assertThat(refused.message()).contains("SIMPLE authentication is not enabled");
                }
                finally {
                    saveLogs(namenode);
                }
            }
        }
        finally {
            kdc.stop();
        }
    }

    /** A keytab with exactly these principals, as each service of a cluster gets its own. */
    private static File keytab(SimpleKdcServer kdc, File file, String... principals) throws Exception
    {
        for (String principal : principals) {
            kdc.createPrincipal(principal);
            kdc.exportPrincipal(principal, file);
        }
        return file;
    }

    private static ServiceConfig secure(String address, String... more)
    {
        Map<String, String> values = new LinkedHashMap<>(Map.of("fs.default.name", address, "hadoop.security.authentication", "kerberos",
                // Named literally: this JVM reaches the NameNode as localhost, which the principal does not carry.
                "dfs.namenode.kerberos.principal", NAMENODE_PRINCIPAL, "lookup.path", "/secure"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("secure-cluster", values);
    }

    /** Makes the directories as the cluster's superuser, signed in from its keytab like an administrator would. */
    private static void prepare(String address, File adminKeytab) throws Exception
    {
        Configuration hadoop = new Configuration();
        hadoop.set("hadoop.security.authentication", "kerberos");
        hadoop.set("dfs.namenode.kerberos.principal", NAMENODE_PRINCIPAL);
        hadoop.set("fs.hdfs.impl.disable.cache", "true");
        UserGroupInformation.setConfiguration(hadoop);
        UserGroupInformation admin = UserGroupInformation.loginUserFromKeytabAndReturnUGI(ADMIN, adminKeytab.getPath());
        admin.doAs((PrivilegedExceptionAction<Void>) () -> {
            try (FileSystem files = FileSystem.newInstance(URI.create(address), hadoop)) {
                ((DistributedFileSystem) files).setSafeMode(SafeModeAction.LEAVE);
                for (String directory : List.of("/secure/alpha", "/secure/beta", "/secure/private")) {
                    files.mkdirs(new Path(directory));
                }
                files.setPermission(new Path("/secure"), new FsPermission((short) 0755));
                files.setPermission(new Path("/secure/private"), new FsPermission((short) 0700));
                files.create(new Path("/secure/notes.txt")).close();
            }
            return null;
        });
    }

    private static GenericContainer<?> namenode(HadoopRuntime runtime, int kdcPort, File nnKeytab, File httpKeytab, TestTls tls)
            throws IOException
    {
        Map<String, String> core = new LinkedHashMap<>();
        core.put("fs.defaultFS", "hdfs://namenode:" + RPC);
        core.put("hadoop.security.authentication", "kerberos");
        core.put("hadoop.security.authorization", "true");
        core.put("hadoop.rpc.protection", "authentication");
        core.put("hadoop.tmp.dir", "/tmp/grantforge-secure");
        Map<String, String> hdfs = new LinkedHashMap<>();
        hdfs.put("dfs.namenode.name.dir", "file:///tmp/grantforge-secure-name");
        hdfs.put("dfs.namenode.rpc-address", "0.0.0.0:" + RPC);
        hdfs.put("dfs.namenode.rpc-bind-host", "0.0.0.0");
        hdfs.put("dfs.namenode.https-address", "0.0.0.0:" + HTTPS);
        hdfs.put("dfs.namenode.https-bind-host", "0.0.0.0");
        hdfs.put("dfs.http.policy", "HTTPS_ONLY");
        hdfs.put("dfs.namenode.kerberos.principal", NAMENODE_PRINCIPAL);
        hdfs.put("dfs.namenode.keytab.file", SECURITY + "/nn.keytab");
        // Every HTTP principal in the keytab: SPNEGO arrives for HTTP/namenode inside, for HTTP/localhost from the test.
        hdfs.put("dfs.web.authentication.kerberos.principal", "*");
        hdfs.put("dfs.web.authentication.kerberos.keytab", SECURITY + "/http.keytab");
        hdfs.put("dfs.block.access.token.enable", "true");
        hdfs.put("dfs.permissions.enabled", "true");
        hdfs.put("dfs.namenode.safemode.extension", "0");
        hdfs.put("dfs.namenode.safemode.min.datanodes", "0");
        hdfs.put("dfs.namenode.handler.count", "2");
        Map<String, String> ssl = new LinkedHashMap<>();
        ssl.put("ssl.server.keystore.location", SECURITY + "/namenode.p12");
        ssl.put("ssl.server.keystore.type", "pkcs12");
        ssl.put("ssl.server.keystore.password", TestTls.KEY_PASSWORD);
        ssl.put("ssl.server.keystore.keypassword", TestTls.KEY_PASSWORD);
        String krb5 = """
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
                """.formatted(REALM, REALM, kdcPort);
        return runtime.container().withExposedPorts(RPC, HTTPS)
                .withCreateContainerCmdModifier(command -> command.withHostName("namenode").withEntrypoint("/bin/bash", "-c"))
                .withEnv("HADOOP_HEAPSIZE_MAX", "256").withEnv("HADOOP_HEAPSIZE", "256").withEnv("HADOOP_ROOT_LOGGER", "INFO,console")
                .withCommand(new String[] {"set -e\n" + HDFS + " namenode -format -force -nonInteractive\nexec " + HDFS + " namenode"})
                .withCopyToContainer(Transferable.of(xml(core)), "/opt/hadoop/etc/hadoop/core-site.xml")
                .withCopyToContainer(Transferable.of(xml(hdfs)), "/opt/hadoop/etc/hadoop/hdfs-site.xml")
                .withCopyToContainer(Transferable.of(xml(ssl)), "/opt/hadoop/etc/hadoop/ssl-server.xml")
                .withCopyToContainer(Transferable.of(krb5), "/etc/krb5.conf")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(nnKeytab.toPath()), 0644), SECURITY + "/nn.keytab")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(httpKeytab.toPath()), 0644), SECURITY + "/http.keytab")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(tls.keyStore()), 0644), SECURITY + "/namenode.p12")
                .waitingFor(Wait.forLogMessage(".*IPC Server listener on " + RPC + ": starting.*", 1)).withStartupTimeout(Duration.ofMinutes(5));
    }

    private static void reason(ThrowingCallable call, LookupException.Reason reason)
    {
        assertThatThrownBy(call).isInstanceOfSatisfying(LookupException.class, failure -> assertThat(failure.getReason()).isEqualTo(reason));
    }

    private static void saveLogs(GenericContainer<?> namenode)
    {
        try {
            java.nio.file.Path logs = java.nio.file.Path.of(System.getProperty("grantforge.hdfs.it.logs", "target/hdfs-testcontainers"),
                    "secure");
            Files.createDirectories(logs);
            Files.writeString(logs.resolve("namenode.log"), namenode.getLogs(), StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException unavailable) {
            // Preserve the original startup or assertion failure even when Docker cannot return its logs.
            System.err.println("Could not save secure NameNode logs: " + unavailable);
        }
    }

    private static String xml(Map<String, String> values)
    {
        StringBuilder xml = new StringBuilder("<configuration>");
        values.forEach((name, value) -> xml.append("<property><name>").append(name).append("</name><value>")
                .append(value).append("</value></property>"));
        return xml.append("</configuration>").toString();
    }
}
