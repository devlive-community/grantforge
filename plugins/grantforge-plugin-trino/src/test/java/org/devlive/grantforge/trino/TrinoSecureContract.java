// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Trino plugin against a coordinator of one release that serves HTTPS with a self-signed certificate and signs
 * users in with a password file or a JWT: both sign-ins work over a truststore that holds the certificate, a wrong
 * password or a token signed by another key is refused, and a coordinator whose certificate cannot be verified is not
 * spoken to.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class TrinoSecureContract
{
    private static final String PASSWORD = "lookup-secret";

    private final TrinoProvider provider = new TrinoProvider();
    private @Nullable TrinoServer server;
    private @Nullable KeyPair signing;
    private Path work = Path.of(".");

    /**
     * The release under test.
     *
     * @return such as {@code 476}
     */
    abstract String version();

    private static KeyPair keys() throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String token(KeyPair keys, String subject) throws Exception
    {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().subject(subject)
                .expirationTime(Date.from(Instant.now().plusSeconds(3600))).build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
        return jwt.serialize();
    }

    @BeforeAll
    void start(@TempDir Path directory) throws Exception
    {
        work = directory;
        TestCertificates.create(work, "coordinator", "trust.p12", "PKCS12");
        TestCertificates.create(work, "stranger", "wrong.jks", "JKS");
        KeyPair keys = keys();
        signing = keys;
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        String hash = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 10).encode(PASSWORD);
        TrinoServer started = new TrinoServer(version()).set("http-server.https.enabled=true").set("http-server.https.port=" + TrinoServer.HTTPS)
                .set("http-server.https.keystore.path=/etc/trino/coordinator.p12").set("http-server.https.keystore.key=" + TestCertificates.KEY_PASSWORD)
                .set("internal-communication.shared-secret=" + Base64.getEncoder().encodeToString(new byte[48]))
                .set("http-server.authentication.type=PASSWORD,JWT").set("http-server.authentication.jwt.key-file=/etc/trino/jwt.pem")
                .file("coordinator.p12", Files.readAllBytes(work.resolve("coordinator.p12")))
                .file("jwt.pem", publicKey.getBytes(StandardCharsets.US_ASCII))
                .file("password.db", ("lookup:" + hash + "\n").getBytes(StandardCharsets.US_ASCII))
                .file("password-authenticator.properties",
                        "password-authenticator.name=file\nfile.password-file=/etc/trino/password.db\n".getBytes(StandardCharsets.US_ASCII));
        server = started;
        started.start();
    }

    @AfterAll
    void stop()
    {
        if (server != null) {
            server.stop();
        }
    }

    private ServiceConfig service(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(TrinoSettings.URL, requireNonNull(server, "server").secureUrl(), TrinoSettings.SSL, "true",
                TrinoSettings.TRUSTSTORE, work.resolve("trust.p12").toString(), TrinoSettings.TRUSTSTORE_PASSWORD, TestCertificates.TRUST_PASSWORD,
                TrinoSettings.TRUSTSTORE_TYPE, "PKCS12", TrinoSettings.TIMEOUT, "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("secure", map);
    }

    private ServiceConfig password(String password)
    {
        return service(TrinoSettings.AUTHENTICATION, TrinoSettings.PASSWORD_AUTH, TrinoSettings.USER, "lookup", TrinoSettings.PASSWORD, password);
    }

    private static LookupException.Reason reason(ServiceConfig config)
    {
        try {
            new TrinoProvider().lookup(new LookupRequest(config, "catalog", "", Map.of(), 10));
        }
        catch (LookupException failed) {
            return failed.getReason();
        }
        throw new AssertionError("the lookup was not refused");
    }

    @Test
    void signsInWithAPasswordOverVerifiedTls()
    {
        ConnectionResult result = provider.testConnection(password(PASSWORD));
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("Trino " + version());
        assertThat(provider.lookup(new LookupRequest(password(PASSWORD), "catalog", "tp", Map.of(), 10))).containsExactly("tpcds", "tpch");
    }

    @Test
    void refusesAWrongPassword()
    {
        assertThat(reason(password("not-the-secret"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(provider.testConnection(password("not-the-secret")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void signsInWithAToken() throws Exception
    {
        ServiceConfig config = service(TrinoSettings.AUTHENTICATION, TrinoSettings.JWT, TrinoSettings.USER, "lookup",
                TrinoSettings.ACCESS_TOKEN, token(requireNonNull(signing, "signing"), "lookup"));
        assertThat(provider.lookup(new LookupRequest(config, "schema", "", Map.of("catalog", List.of("tpch")), 50))).contains("sf1", "tiny");
    }

    @Test
    void refusesATokenSignedByAnotherKey() throws Exception
    {
        ServiceConfig config = service(TrinoSettings.AUTHENTICATION, TrinoSettings.JWT, TrinoSettings.USER, "lookup",
                TrinoSettings.ACCESS_TOKEN, token(keys(), "lookup"));
        assertThat(reason(config)).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
    }

    @Test
    void doesNotSpeakToACoordinatorItCannotVerify()
    {
        ServiceConfig wrong = service(TrinoSettings.AUTHENTICATION, TrinoSettings.PASSWORD_AUTH, TrinoSettings.USER, "lookup", TrinoSettings.PASSWORD,
                PASSWORD, TrinoSettings.TRUSTSTORE, work.resolve("wrong.jks").toString(), TrinoSettings.TRUSTSTORE_TYPE, "JKS");
        assertThat(reason(wrong)).isEqualTo(LookupException.Reason.UNREACHABLE);
        Map<String, String> unchecked = new HashMap<>(password(PASSWORD).values());
        unchecked.remove(TrinoSettings.TRUSTSTORE);
        unchecked.remove(TrinoSettings.TRUSTSTORE_PASSWORD);
        unchecked.put(TrinoSettings.VERIFICATION, "NONE");
        ConnectionResult result = provider.testConnection(new ServiceConfig("secure", unchecked));
        assertThat(result.status()).as("an operator may turn verification off: " + result.message()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }

    @Test
    void refusesASignInOverPlainHttp()
    {
        ServiceConfig plain = service(TrinoSettings.URL, requireNonNull(server, "server").url(), TrinoSettings.SSL, "false",
                TrinoSettings.AUTHENTICATION, TrinoSettings.PASSWORD_AUTH, TrinoSettings.USER, "lookup", TrinoSettings.PASSWORD, PASSWORD);
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(plain, "catalog", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
