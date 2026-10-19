// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-signed certificates for {@code localhost}, made with the JDK's keytool: a coordinator's PKCS12 key store and a
 * truststore holding only its certificate, as an operator would hand GrantForge for a coordinator.
 */
final class TestCertificates
{
    /** The password of every key store and key. */
    static final String KEY_PASSWORD = "server-secret";
    /** The password of every truststore. */
    static final String TRUST_PASSWORD = "trust-secret";

    private TestCertificates()
    {
    }

    /**
     * Makes {@code <name>.p12} with a new key and certificate, and a truststore that holds the certificate.
     *
     * @param directory where the stores go
     * @param name the key's alias and the key store's name
     * @param trustStore the truststore's file name
     * @param type JKS or PKCS12, the truststore's type
     * @throws IOException if keytool fails
     * @throws InterruptedException if waiting for keytool is interrupted
     */
    static void create(Path directory, String name, String trustStore, String type) throws IOException, InterruptedException
    {
        keytool(directory, "-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext",
                "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore", name + ".p12", "-storepass", KEY_PASSWORD, "-keypass", KEY_PASSWORD);
        keytool(directory, "-exportcert", "-alias", name, "-keystore", name + ".p12", "-storepass", KEY_PASSWORD, "-rfc", "-file", name + ".pem");
        keytool(directory, "-importcert", "-noprompt", "-alias", name, "-file", name + ".pem", "-storetype", type, "-keystore", trustStore,
                "-storepass", TRUST_PASSWORD);
    }

    private static void keytool(Path directory, String... arguments) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException("keytool failed: " + output);
        }
    }
}
