// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.apache.zookeeper.server.ZooKeeperServer;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the packaged shaded agent with its own policy/signing classes and a real, verified cached snapshot. */
class ZooKeeperAgentJarIT
{
    @TempDir
    Path work;

    @Test
    void shadedJarLoadsAndEnforcesASignedCachedPolicy() throws Exception
    {
        Path jar = Path.of(System.getProperty("grantforge.zookeeper.agent.jar"));
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("org/apache/zookeeper/ZooKeeper.class")).isNull();
            assertThat(zip.getEntry("javax/servlet/http/HttpServletRequest.class")).isNull();
            assertThat(zip.getEntry("org/junit/jupiter/api/Test.class")).isNull();
            assertThat(zip.getEntry("org/devlive/grantforge/zookeeper/agent/internal/jackson/databind/ObjectMapper.class")).isNotNull();
        }
        Map<String, String> settings = signedCache();
        ZooKeeperServer.setEnableEagerACLCheck(false);
        try (AgentLoader loader = new AgentLoader(jar.toUri().toURL())) {
            Class<?> runtimeType = loader.loadClass(ZooKeeperAgentRuntime.class.getName());
            Object runtime = runtimeType.getConstructor().newInstance();
            try {
                runtimeType.getMethod("start", Map.class, String.class).invoke(runtime, settings, "jar-smoke");
                Object authorizer = runtimeType.getMethod("authorizer").invoke(runtime);
                assertThat(authorizer.getClass().getClassLoader()).isSameAs(loader);
                assertThat(loader.loadClass(AccessRequest.class.getName()).getClassLoader()).isSameAs(loader);
                java.lang.reflect.Method authorize = authorizer.getClass().getMethod("authorize", String.class, String.class, int.class,
                        String.class);
                assertThat(authorize.invoke(authorizer, "alice@REALM", "/protected", 1, "127.0.0.1")).isEqualTo(true);
                assertThat(authorize.invoke(authorizer, "bob@REALM", "/protected", 1, "127.0.0.1")).isEqualTo(false);
                assertThat(authorize.invoke(authorizer, "alice@REALM", "/outside", 1, "127.0.0.1")).isEqualTo(false);
                Class<?> provider = loader.loadClass(GrantForgeSaslAuthenticationProvider.class.getName());
                java.lang.reflect.Constructor<?> constructor = provider.getDeclaredConstructor(authorizer.getClass());
                constructor.setAccessible(true);
                Object loaded = constructor.newInstance(authorizer);
                assertThat(provider.getMethod("getScheme").invoke(loaded)).isEqualTo("sasl");
            }
            finally {
                runtimeType.getMethod("close").invoke(runtime);
            }
        }
    }

    private Map<String, String> signedCache() throws Exception
    {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String encoded = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String json = """
                {"format":1,"service":"ensemble","serviceType":"zookeeper","serviceEnabled":true,"policyVersion":1,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                  "accessTypes":[{"name":"read"}],"conditions":[]},
                 "policies":[{"id":"1","name":"read","type":"ACCESS","priority":"NORMAL","document":{
                  "resources":{"path":{"values":["/protected"],"recursive":false,"excludes":false}},
                  "allow":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["read"]}]}}]}
                """;
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(body);
        Properties metadata = new Properties();
        metadata.setProperty("etag", "test");
        metadata.setProperty("keyId", SigningKey.of(encoded).keyId());
        metadata.setProperty("signingKey", encoded);
        metadata.setProperty("signature", Base64.getEncoder().encodeToString(signer.sign()));
        Path cache = Files.createDirectories(work.resolve("cache"));
        Files.write(cache.resolve("snapshot.json"), body);
        try (OutputStream out = Files.newOutputStream(cache.resolve("snapshot.properties"))) {
            metadata.store(out, "signed test snapshot");
        }
        Path token = Files.writeString(work.resolve("token"), "test-token");
        Path key = Files.writeString(work.resolve("key"), encoded);
        Map<String, String> settings = new HashMap<>();
        // No network service is needed: cache is verified synchronously and refresh errors retain the last policy.
        settings.put("grantforge.server.url", "http://127.0.0.1:1");
        settings.put("grantforge.token.file", token.toString());
        settings.put("grantforge.signing.key.file", key.toString());
        settings.put("grantforge.instance", "jar-test");
        settings.put("grantforge.cache.dir", cache.toString());
        settings.put("grantforge.connect.timeout.ms", "50");
        settings.put("grantforge.read.timeout.ms", "50");
        return settings;
    }

    /** Agent classes come from the JAR; only the host's ZooKeeper API is delegated. */
    private static final class AgentLoader
            extends URLClassLoader
    {
        AgentLoader(URL jar)
        {
            super(new URL[] {jar}, ZooKeeperAgentJarIT.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
        {
            synchronized (getClassLoadingLock(name)) {
                if (!name.startsWith("org.devlive.grantforge.")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
