// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A single-node Elasticsearch cluster of one release in a container, with security on: TLS on HTTP with the test
 * certificates, the {@value #ADMIN} superuser, and a PKI realm that signs in the test client certificate as
 * {@value #CERTIFICATE_USER}. The cluster runs a trial license, as PKI realms need Gold and above. Requests run as
 * {@value #ADMIN} through curl in the container.
 */
final class ElasticsearchNode
        implements AutoCloseable
{
    static final String ADMIN = "elastic";
    static final String ADMIN_PASSWORD = "GrantForge-Test-2026!";
    /** The user the PKI realm takes from the test client certificate's common name. */
    static final String CERTIFICATE_USER = "client";

    private static final String CERTS = "/usr/share/elasticsearch/config/certs/";

    private final TestCertificates certificates;
    private final GenericContainer<?> container;

    /**
     * Starts the cluster.
     *
     * @param version the release, such as {@code 9.5.5}
     * @param certificates the test certificates, whose server certificate the node serves
     */
    ElasticsearchNode(String version, TestCertificates certificates)
    {
        this.certificates = certificates;
        GenericContainer<?> node = new GenericContainer<>(DockerImageName.parse("elasticsearch:" + version)).withExposedPorts(9200)
                .withEnv("discovery.type", "single-node").withEnv("ELASTIC_PASSWORD", ADMIN_PASSWORD).withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withEnv("xpack.security.enabled", "true").withEnv("xpack.license.self_generated.type", "trial").withEnv("xpack.ml.enabled", "false")
                .withEnv("xpack.security.http.ssl.enabled", "true").withEnv("xpack.security.http.ssl.certificate", CERTS + "server.pem")
                .withEnv("xpack.security.http.ssl.key", CERTS + "server-key.pem").withEnv("xpack.security.http.ssl.certificate_authorities", CERTS + "ca.pem")
                .withEnv("xpack.security.http.ssl.client_authentication", "optional").withEnv("xpack.security.authc.realms.pki.pki1.order", "1")
                .waitingFor(Wait.forHttps("/_security/_authenticate").allowInsecure().forPort(9200).withBasicCredentials(ADMIN, ADMIN_PASSWORD)
                        .forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));
        for (String file : List.of("ca.pem", "server.pem", "server-key.pem")) {
            node.withCopyFileToContainer(MountableFile.forHostPath(certificates.file(file), 0644), CERTS + file);
        }
        container = node;
        container.start();
    }

    /** The node by the name its certificate is for. */
    String url()
    {
        return "https://localhost:" + container.getMappedPort(9200);
    }

    /** The test CA's certificate, PEM. */
    String caCertificate()
    {
        return certificates.file("ca.pem");
    }

    /** The test client certificate, PEM. */
    String clientCertificate()
    {
        return certificates.file("client.pem");
    }

    /** The test client certificate's private key, PKCS #8 PEM. */
    String clientKey()
    {
        return certificates.file("client-pkcs8.pem");
    }

    /**
     * Sends a request as {@value #ADMIN}.
     *
     * @param method the HTTP method
     * @param path the path and query
     * @param body the JSON body, or empty for none
     * @return the response body
     */
    String call(String method, String path, String body) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>(List.of("curl", "-sk", "-u", ADMIN + ":" + ADMIN_PASSWORD, "-X", method,
                "-H", "Content-Type: application/json", "-w", "\n%{http_code}", "https://localhost:9200" + path));
        if (!body.isEmpty()) {
            command.addAll(List.of("--data-binary", body));
        }
        var result = container.execInContainer(command.toArray(new String[0]));
        String output = result.getStdout();
        int end = output.lastIndexOf('\n');
        String status = output.substring(end + 1).strip();
        if (result.getExitCode() != 0 || !status.startsWith("2")) {
            throw new IllegalStateException(method + " " + path + ": " + status + " " + output + result.getStderr());
        }
        return output.substring(0, end);
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
