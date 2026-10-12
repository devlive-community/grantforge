// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.client.ZKClientConfig;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZooKeeperClientTest
{
    private static ServiceConfig config(String... pairs)
    {
        Map<String, String> values = new HashMap<>(Map.of(ZooKeeperProvider.CONNECT, "localhost:2181"));
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return new ServiceConfig("zookeeper", values);
    }

    @Test
    void aPlainServiceDoesNotInheritAnotherClientsTlsOrSaslChoice() throws IOException
    {
        try (SystemProperties properties = new SystemProperties()) {
            properties.set(ZKClientConfig.SECURE_CLIENT, "true");
            properties.set(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "true");
            ZKClientConfig settings = new ZooKeeperClient(config()).settings();
            assertThat(settings.getProperty(ZKClientConfig.SECURE_CLIENT)).isEqualTo("false");
            assertThat(settings.getProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY)).isEqualTo("false");
            assertThat(System.getProperty(ZKClientConfig.SECURE_CLIENT)).isEqualTo("true");
            assertThat(System.getProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY)).isEqualTo("true");
        }
    }

    @Test
    void tlsDoesNotBorrowGlobalStoresOrPasswordFilesAndStillVerifiesHostnames() throws IOException
    {
        List<String> inherited = List.of(ZooKeeperProvider.TRUSTSTORE, ZooKeeperProvider.TRUSTSTORE_PASSWORD,
                ZooKeeperProvider.TRUSTSTORE_TYPE, ZooKeeperProvider.KEYSTORE, ZooKeeperProvider.KEYSTORE_PASSWORD,
                ZooKeeperProvider.KEYSTORE_TYPE, "zookeeper.ssl.trustStore.passwordPath", "zookeeper.ssl.keyStore.passwordPath");
        try (SystemProperties properties = new SystemProperties()) {
            for (String property : inherited) {
                properties.set(property, "another-service");
            }
            properties.set("zookeeper.ssl.hostnameVerification", "false");
            ZKClientConfig settings = new ZooKeeperClient(config(ZooKeeperProvider.SECURE, "true")).settings();
            for (String property : inherited) {
                assertThat(settings.getProperty(property)).as(property).isNull();
                assertThat(System.getProperty(property)).as(property).isEqualTo("another-service");
            }
            assertThat(settings.getProperty(ZKClientConfig.SECURE_CLIENT)).isEqualTo("true");
            assertThat(settings.getProperty("zookeeper.ssl.hostnameVerification")).isEqualTo("true");
            assertThat(System.getProperty("zookeeper.ssl.hostnameVerification")).isEqualTo("false");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" leading and trailing ", "      "})
    void tlsStorePasswordsRetainTheirExactWhitespace(String password) throws IOException
    {
        ZKClientConfig settings = new ZooKeeperClient(config(ZooKeeperProvider.SECURE, "true",
                ZooKeeperProvider.TRUSTSTORE, " trust.p12 ", ZooKeeperProvider.TRUSTSTORE_PASSWORD, password,
                ZooKeeperProvider.KEYSTORE, " key.p12 ", ZooKeeperProvider.KEYSTORE_PASSWORD, password)).settings();
        assertThat(settings.getProperty(ZooKeeperProvider.TRUSTSTORE)).isEqualTo("trust.p12");
        assertThat(settings.getProperty(ZooKeeperProvider.KEYSTORE)).isEqualTo("key.p12");
        assertThat(settings.getProperty(ZooKeeperProvider.TRUSTSTORE_PASSWORD)).isEqualTo(password);
        assertThat(settings.getProperty(ZooKeeperProvider.KEYSTORE_PASSWORD)).isEqualTo(password);
    }

    @Test
    void eachServiceHasAFiniteRequestDeadlineInsteadOfAnAmbientInfiniteTimeout() throws IOException
    {
        try (SystemProperties properties = new SystemProperties()) {
            properties.set(ZKClientConfig.ZOOKEEPER_REQUEST_TIMEOUT, "0");
            assertThat(new ZooKeeperClient(config()).settings().getProperty(ZKClientConfig.ZOOKEEPER_REQUEST_TIMEOUT))
                    .isEqualTo("8000");
            assertThat(new ZooKeeperClient(config(ZooKeeperProvider.REQUEST_TIMEOUT, "1000")).settings()
                    .getProperty(ZKClientConfig.ZOOKEEPER_REQUEST_TIMEOUT)).isEqualTo("1000");
            assertThat(System.getProperty(ZKClientConfig.ZOOKEEPER_REQUEST_TIMEOUT)).isEqualTo("0");
        }
    }

    @Test
    void anotherClientsServerPrincipalDoesNotChooseThisServicesIdentity() throws IOException
    {
        try (SystemProperties properties = new SystemProperties()) {
            properties.set(ZKClientConfig.ZOOKEEPER_SERVER_PRINCIPAL, "zookeeper/another@EXAMPLE.COM");
            assertThat(new ZooKeeperClient(config()).settings().getProperty(ZKClientConfig.ZOOKEEPER_SERVER_PRINCIPAL)).isNull();
            assertThat(System.getProperty(ZKClientConfig.ZOOKEEPER_SERVER_PRINCIPAL))
                    .isEqualTo("zookeeper/another@EXAMPLE.COM");
        }
    }

    private static final class SystemProperties
            implements AutoCloseable
    {
        private final Map<String, @Nullable String> previous = new HashMap<>();

        void set(String name, String value)
        {
            if (!previous.containsKey(name)) {
                previous.put(name, System.getProperty(name));
            }
            System.setProperty(name, value);
        }

        @Override
        public void close()
        {
            for (Map.Entry<String, @Nullable String> entry : previous.entrySet()) {
                String value = entry.getValue();
                if (value == null) {
                    System.clearProperty(entry.getKey());
                }
                else {
                    System.setProperty(entry.getKey(), value);
                }
            }
        }
    }
}
