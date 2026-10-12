// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Creates short-lived test certificates with the running JDK, without another cryptography dependency. */
final class ZooKeeperTlsCertificates
{
    static final String CLIENT_PASSWORD = "  tls-password  ";
    static final String SERVER_PASSWORD = "server-password";

    private final Path directory;

    ZooKeeperTlsCertificates(Path directory) throws IOException, InterruptedException
    {
        this.directory = directory;
        Files.createDirectories(directory);
        keyPair("ca", "ca.p12", SERVER_PASSWORD, "CN=GrantForge Test CA", "bc:c=ca:true");
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", file("ca.p12"), "-storepass", SERVER_PASSWORD,
                "-file", file("ca.pem"));
        signed("server", SERVER_PASSWORD, "CN=localhost", "serverAuth", "dns:localhost,ip:127.0.0.1,dns:zk-tls");
        signed("wrong-host", SERVER_PASSWORD, "CN=mismatch.invalid", "serverAuth", "dns:mismatch.invalid");
        signed("client", CLIENT_PASSWORD, "CN=grantforge-client", "clientAuth", "dns:localhost,ip:127.0.0.1");
        trust("trust.p12", CLIENT_PASSWORD, "ca.pem");
        trust("server-trust.p12", SERVER_PASSWORD, "ca.pem");
        keyPair("unrelated", "unrelated.p12", SERVER_PASSWORD, "CN=Unrelated Test CA", "bc:c=ca:true");
        keytool("-exportcert", "-rfc", "-alias", "unrelated", "-keystore", file("unrelated.p12"), "-storepass", SERVER_PASSWORD,
                "-file", file("unrelated.pem"));
        trust("wrong-trust.p12", CLIENT_PASSWORD, "unrelated.pem");
    }

    Path serverStore()
    {
        return directory.resolve("server.p12");
    }

    Path wrongHostStore()
    {
        return directory.resolve("wrong-host.p12");
    }

    Path clientStore()
    {
        return directory.resolve("client.p12");
    }

    Path trustStore()
    {
        return directory.resolve("trust.p12");
    }

    Path wrongTrustStore()
    {
        return directory.resolve("wrong-trust.p12");
    }

    Path serverTrustStore()
    {
        return directory.resolve("server-trust.p12");
    }

    Path log()
    {
        return directory.resolve("keytool.log");
    }

    private void signed(String alias, String password, String subject, String usage, String san) throws IOException, InterruptedException
    {
        String store = alias + ".p12";
        keyPair(alias, store, password, subject, "SAN=" + san);
        keytool("-certreq", "-alias", alias, "-keystore", file(store), "-storepass", password, "-file", file(alias + ".csr"));
        keytool("-gencert", "-rfc", "-alias", "ca", "-keystore", file("ca.p12"), "-storepass", SERVER_PASSWORD,
                "-infile", file(alias + ".csr"), "-outfile", file(alias + ".pem"), "-validity", "2",
                "-ext", "KU=digitalSignature,keyEncipherment", "-ext", "EKU=" + usage, "-ext", "SAN=" + san);
        keytool("-importcert", "-noprompt", "-alias", "ca", "-keystore", file(store), "-storepass", password, "-file", file("ca.pem"));
        keytool("-importcert", "-noprompt", "-alias", alias, "-keystore", file(store), "-storepass", password,
                "-file", file(alias + ".pem"));
    }

    private void keyPair(String alias, String store, String password, String subject, String extension) throws IOException, InterruptedException
    {
        keytool("-genkeypair", "-alias", alias, "-keystore", file(store), "-storetype", "PKCS12", "-storepass", password,
                "-keypass", password, "-dname", subject, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-ext", extension);
    }

    private void trust(String store, String password, String certificate) throws IOException, InterruptedException
    {
        keytool("-importcert", "-noprompt", "-alias", "ca", "-keystore", file(store), "-storetype", "PKCS12",
                "-storepass", password, "-file", file(certificate));
    }

    private String file(String name)
    {
        return directory.resolve(name).toAbsolutePath().toString();
    }

    private void keytool(String... arguments) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log().toFile())).start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                throw new IOException("keytool did not finish within 30 seconds; see " + log());
            }
            if (process.exitValue() != 0) {
                throw new IOException("keytool failed with exit " + process.exitValue() + "; see " + log());
            }
        }
        finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
