// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProxySettingsTest
{
    @TempDir
    Path directory;

    private Map<String, String> minimal() throws IOException
    {
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.server.url", "https://grantforge.example.com");
        values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), "gf_agent_token\n").toString());
        values.put("grantforge.instance", "mysql-proxy-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        values.put("grantforge.mysql.host", "mysql.internal");
        values.put("grantforge.mysql.metadata.user", "grantforge_meta");
        values.put("grantforge.mysql.metadata.password.file", Files.writeString(directory.resolve("meta"), " meta-secret \n").toString());
        return values;
    }

    @Test
    void readsTheSettingsWithTheirDefaults() throws IOException
    {
        ProxySettings settings = ProxySettings.parse(minimal(), "2026.1.0");
        assertThat(settings.listen().getPort()).isEqualTo(3307);
        assertThat(settings.listen().getAddress().isAnyLocalAddress()).isTrue();
        assertThat(settings.backend().host()).isEqualTo("mysql.internal");
        assertThat(settings.backend().port()).isEqualTo(3306);
        assertThat(settings.backend().backendTls()).isNull();
        assertThat(settings.backend().clientTls()).isNull();
        assertThat(settings.metadataUrl()).isEqualTo("jdbc:mariadb://mysql.internal:3306/");
        assertThat(settings.metadata()).containsEntry("user", "grantforge_meta").containsEntry("password", "meta-secret")
                .containsEntry("sslMode", "disable").containsEntry("allowLocalInfile", "false");
        assertThat(settings.keep()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.checkThreads()).isEqualTo(8);
        assertThat(settings.agent().token()).isEqualTo("gf_agent_token");
        assertThat(settings.agent().instance()).isEqualTo("mysql-proxy-1");
    }

    @Test
    void verifiesTheServerAsAsked() throws IOException
    {
        Map<String, String> values = minimal();
        values.put("grantforge.mysql.tls", "verify-identity");
        values.put("grantforge.proxy.host", "127.0.0.1");
        values.put("grantforge.proxy.port", "13306");
        values.put("grantforge.mysql.port", "3307");
        values.put("grantforge.mysql.host", "::1");
        ProxySettings settings = ProxySettings.parse(values, "2026.1.0");
        assertThat(settings.backend().backendTls()).isNotNull();
        assertThat(settings.metadata()).containsEntry("sslMode", "verify-full").doesNotContainKey("allowPublicKeyRetrieval");
        assertThat(settings.metadataUrl()).isEqualTo("jdbc:mariadb://[::1]:3307/");
        assertThat(settings.listen().getPort()).isEqualTo(13306);
        values.put("grantforge.mysql.tls", "required");
        assertThat(ProxySettings.parse(values, "2026.1.0").metadata()).containsEntry("sslMode", "trust");
    }

    @Test
    void refusesWhatDoesNotFit() throws IOException
    {
        Map<String, String> values = minimal();
        values.remove("grantforge.mysql.host");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(values, "1")).withMessageContaining("grantforge.mysql.host");
        Map<String, String> tls = minimal();
        tls.put("grantforge.mysql.tls", "sometimes");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(tls, "1")).withMessageContaining("verify-identity");
        Map<String, String> ca = minimal();
        ca.put("grantforge.mysql.tls.ca", "/etc/ca.pem");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(ca, "1")).withMessageContaining("verify-ca");
        Map<String, String> half = minimal();
        half.put("grantforge.proxy.tls.cert", "/etc/proxy.pem");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(half, "1")).withMessageContaining("go together");
        Map<String, String> required = minimal();
        required.put("grantforge.proxy.tls.required", "true");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(required, "1")).withMessageContaining("does not offer");
        Map<String, String> threads = minimal();
        threads.put("grantforge.proxy.check.threads", "0");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(threads, "1")).withMessageContaining("between 1 and 256");
        assertThatIllegalArgumentException().isThrownBy(() -> ProxySettings.parse(minimal(), " ")).withMessageContaining("version");
    }

    @Test
    void readsAPropertiesFileAndLetsSystemPropertiesWin() throws IOException
    {
        Path file = Files.writeString(directory.resolve("proxy.properties"), "grantforge.mysql.host = from-file\ngrantforge.proxy.port=3310\n");
        System.setProperty("grantforge.proxy.port", "3311");
        try {
            Map<String, String> values = MySqlProxyMain.settings(file.toString());
            assertThat(values).containsEntry("grantforge.mysql.host", "from-file").containsEntry("grantforge.proxy.port", "3311");
        }
        finally {
            System.clearProperty("grantforge.proxy.port");
        }
        assertThat(MySqlProxyMain.version()).isNotBlank();
    }
}
