// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The KDC of a Kerberos cluster, in this JVM, with a keytab for each kind of daemon, a ticket cache for each user and
 * the TLS stores of HTTPS only: everything {@link HadoopContainers} copies into the daemons. The users' tickets come
 * from this JVM's Kerberos client, since the image's MIT kinit fails the KDC's pre-authentication.
 */
final class ClusterKdc
        implements AutoCloseable
{
    static final String REALM = "EXAMPLE.COM";

    /** Users with a ticket cache; nn is the superuser, the NameNodes' own short name. */
    static final List<String> USERS = List.of("nn", "alice", "mallory");

    private final SimpleKdcServer kdc;
    private final KerberosSetup setup;

    /**
     * Starts the KDC.
     *
     * @param work where its files go
     * @param hosts the daemons' host names, each of which gets its HTTP principal and a name in the certificate
     * @throws Exception if it does not start
     */
    ClusterKdc(Path work, List<String> hosts) throws Exception
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        kdc = new SimpleKdcServer();
        kdc.setWorkDir(work.toFile());
        kdc.setKdcRealm(REALM);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(port);
        kdc.init();
        kdc.start();
        try {
            Path security = Files.createDirectories(work.resolve("security"));
            keytab(security, "nn-service.keytab", "nn/namenode");
            keytab(security, "dn-service.keytab", "dn/datanode");
            keytab(security, "jn-service.keytab", "nn/journal");
            keytab(security, "zk-service.keytab", "zookeeper/zookeeper");
            keytab(security, "http-service.keytab", hosts.stream().map(host -> "HTTP/" + host).toArray(String[]::new));
            KrbClient client = kdc.getKrbClient();
            for (String user : USERS) {
                Path keytab = work.resolve(user + ".keytab");
                keytab(work, user + ".keytab", user);
                client.storeTicket(client.requestTgt(user + "@" + REALM, keytab.toFile()), security.resolve("krb5cc_" + user).toFile());
            }
            stores(security, hosts);
            setup = new KerberosSetup(REALM, port, security);
        }
        catch (Exception | AssertionError failed) {
            kdc.stop();
            throw failed;
        }
    }

    KerberosSetup setup()
    {
        return setup;
    }

    /** A keytab with exactly these principals, as each daemon and user of a cluster gets its own. */
    private void keytab(Path directory, String name, String... principals) throws Exception
    {
        File file = directory.resolve(name).toFile();
        for (String principal : principals) {
            kdc.createPrincipal(principal + "@" + REALM);
            kdc.exportPrincipal(principal + "@" + REALM, file);
        }
    }

    /** A self-signed certificate for all daemons, with the JDK's keytool, and a truststore holding it. */
    private static void stores(Path security, List<String> hosts) throws IOException, InterruptedException
    {
        Path keys = security.resolve("keystore.p12");
        Path certificate = security.resolve("cluster.crt");
        Path trust = security.resolve("truststore.jks");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        String password = KerberosSetup.STORE_PASSWORD;
        List<String> names = new ArrayList<>(hosts);
        names.add("localhost");
        String san = "san=" + String.join(",", names.stream().map(host -> "dns:" + host).toList());
        // The legacy PKCS12 algorithms, which the Java 8 of the older Hadoop images reads too.
        keytool(List.of(keytool, "-J-Dkeystore.pkcs12.legacy", "-genkeypair", "-alias", "cluster", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=" + hosts.get(0), "-ext", san, "-keystore", keys.toString(), "-storetype", "PKCS12",
                "-storepass", password, "-keypass", password));
        keytool(List.of(keytool, "-exportcert", "-alias", "cluster", "-keystore", keys.toString(), "-storepass", password,
                "-file", certificate.toString()));
        keytool(List.of(keytool, "-importcert", "-noprompt", "-alias", "cluster", "-file", certificate.toString(), "-keystore",
                trust.toString(), "-storetype", "JKS", "-storepass", password));
        // Only the stores go to the daemons.
        Files.delete(certificate);
    }

    private static void keytool(List<String> command) throws IOException, InterruptedException
    {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IOException("keytool failed: " + output);
        }
    }

    @Override
    public void close()
    {
        try {
            kdc.stop();
        }
        catch (Exception unstoppable) {
            System.err.println("Could not stop the KDC: " + unstoppable);
        }
    }
}
