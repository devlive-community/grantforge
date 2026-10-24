// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * A standalone Pulsar broker of one release in a container, signing clients in with tokens of its own secret key and
 * deciding access with Pulsar's own authorization: {@code admin} is its superuser and may list everything, other roles
 * may not. Tokens are signed for any role with {@link #token}.
 */
final class PulsarCluster
        implements AutoCloseable
{
    static final String SUPERUSER = "admin";
    static final int WEB_PORT = 8080;
    static final int BROKER_PORT = 6650;

    private final byte[] secret = new byte[32];
    private final GenericContainer<?> container;

    PulsarCluster(String version)
    {
        this(version, Map.of());
    }

    /**
     * A broker with settings of its own on top of those that sign clients in.
     *
     * @param version the release, such as {@code 4.2.5}
     * @param settings more of the broker's settings, by name
     */
    @SuppressWarnings("resource")
    PulsarCluster(String version, Map<String, String> settings)
    {
        new SecureRandom().nextBytes(secret);
        Map<String, String> broker = new HashMap<>();
        broker.put("authenticationEnabled", "true");
        broker.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken");
        broker.put("tokenSecretKey", "data:;base64," + Base64.getEncoder().encodeToString(secret));
        broker.put("authorizationEnabled", "true");
        broker.put("superUserRoles", SUPERUSER);
        broker.put("brokerClientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        broker.put("brokerClientAuthenticationParameters", "token:" + token(SUPERUSER));
        broker.put("allowAutoTopicCreation", "true");
        broker.putAll(settings);
        // The settings go into the broker's file, as an operator writes them: the shell of Pulsar 3.0's image drops
        // environment variables whose names have dots, such as PULSAR_PREFIX_grantforge.token.file.
        StringBuilder file = new StringBuilder("\n");
        broker.forEach((name, value) -> file.append(name).append('=').append(value).append('\n'));
        container = new GenericContainer<>(DockerImageName.parse("apachepulsar/pulsar:" + version)).withExposedPorts(WEB_PORT, BROKER_PORT)
                .withEnv("PULSAR_MEM", "-Xms256m -Xmx512m -XX:MaxDirectMemorySize=256m")
                .withCopyToContainer(Transferable.of(file.toString()), "/pulsar/conf/tests.conf")
                .withCommand("sh", "-c", "cat conf/tests.conf >> conf/standalone.conf && exec bin/pulsar standalone --no-functions-worker -nss")
                .waitingFor(Wait.forLogMessage(".*messaging service is ready.*", 1).withStartupTimeout(Duration.ofMinutes(4)));
    }

    GenericContainer<?> container()
    {
        return container;
    }

    void start()
    {
        container.start();
    }

    /** The broker's web service, as a service names it. */
    String webServiceUrl()
    {
        return "http://" + container.getHost() + ":" + container.getMappedPort(WEB_PORT);
    }

    /** The broker's binary protocol, for producers and consumers. */
    String serviceUrl()
    {
        return "pulsar://" + container.getHost() + ":" + container.getMappedPort(BROKER_PORT);
    }

    /**
     * Signs a token for a role with the broker's secret key.
     *
     * @param role the role
     * @return the token
     */
    String token(String role)
    {
        return token(role, secret);
    }

    /**
     * Signs a token for a role with a key, for tokens the broker refuses.
     *
     * @param role the role
     * @param key the key, at least 32 bytes
     * @return the token
     */
    static String token(String role, byte[] key)
    {
        try {
            SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder().subject(role).build());
            token.sign(new MACSigner(key));
            return token.serialize();
        }
        catch (JOSEException broken) {
            throw new IllegalStateException(broken);
        }
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
