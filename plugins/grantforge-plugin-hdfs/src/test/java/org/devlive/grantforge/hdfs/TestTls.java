// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A self-signed certificate for 127.0.0.1, localhost (Hadoop connects by name) and the test NameNode's host, made
 * with the JDK's keytool: the server's key store, and a truststore holding only that certificate, as an operator
 * would hand GrantForge for a NameNode.
 *
 * @param keyStore the server's PKCS12 key store
 * @param trustStore the client's JKS truststore
 */
public record TestTls(Path keyStore, Path trustStore)
{
    /** The password of the server's key store and of its key. */
    public static final String KEY_PASSWORD = "server-secret";
    /** The password of the truststore. */
    public static final String TRUST_PASSWORD = "trust-secret";

    /**
     * Makes the certificate and both stores.
     *
     * @param directory where the stores go
     * @return the stores
     * @throws IOException if keytool fails
     * @throws InterruptedException if waiting for keytool is interrupted
     */
    public static TestTls create(Path directory) throws IOException, InterruptedException
    {
        Path keys = directory.resolve("namenode.p12");
        Path certificate = directory.resolve("namenode.crt");
        Path trust = directory.resolve("trust.jks");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(List.of(keytool, "-genkeypair", "-alias", "namenode", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=127.0.0.1", "-ext", "san=ip:127.0.0.1,dns:localhost,dns:namenode", "-keystore", keys.toString(), "-storetype", "PKCS12",
                "-storepass", KEY_PASSWORD, "-keypass", KEY_PASSWORD));
        run(List.of(keytool, "-exportcert", "-alias", "namenode", "-keystore", keys.toString(), "-storepass", KEY_PASSWORD,
                "-file", certificate.toString()));
        run(List.of(keytool, "-importcert", "-noprompt", "-alias", "namenode", "-file", certificate.toString(), "-keystore",
                trust.toString(), "-storetype", "JKS", "-storepass", TRUST_PASSWORD));
        return new TestTls(keys, trust);
    }

    /**
     * The server side of the certificate.
     *
     * @return a TLS context with the server's key
     * @throws IOException if the key store cannot be read
     * @throws GeneralSecurityException if the key store cannot be opened
     */
    SSLContext serverContext() throws IOException, GeneralSecurityException
    {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStore)) {
            store.load(input, KEY_PASSWORD.toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, KEY_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }

    private static void run(List<String> command) throws IOException, InterruptedException
    {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IOException("keytool failed: " + output);
        }
    }
}
