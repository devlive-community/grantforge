// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.security.HiveAuthenticationProvider;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthorizer;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzPluginException;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrantForgeHiveAuthorizerFactoryTest
{
    @TempDir
    Path temporary;

    @Test
    void startsTheAgentWithTheFirstSessionOnly() throws Exception
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        @SuppressWarnings("unchecked")
        Function<AgentSettings, GrantForgeAgent> agents = mock(Function.class);
        when(agents.apply(any(AgentSettings.class))).thenReturn(agent);
        HiveAgentRuntime runtime = new HiveAgentRuntime(agents);
        Map<String, String> settings = new HashMap<>();
        settings.put("grantforge.token.file", Files.writeString(temporary.resolve("token"), "token").toString());
        settings.put("grantforge.server.url", "https://grantforge.example.com/");
        settings.put("grantforge.instance", "hs2-1");
        settings.put("grantforge.cache.dir", temporary.toString());
        GrantForgeHiveAuthorizerFactory factory = new GrantForgeHiveAuthorizerFactory(runtime, () -> settings);
        HiveAuthenticationProvider authenticator = mock(HiveAuthenticationProvider.class);
        try {
            HiveAuthorizer first = factory.createHiveAuthorizer(null, null, authenticator, null);
            HiveAuthorizer second = factory.createHiveAuthorizer(null, null, authenticator, null);
            assertThat(first).isInstanceOf(GrantForgeHiveAuthorizer.class).isNotSameAs(second);
            verify(agents, times(1)).apply(any(AgentSettings.class));
            when(authenticator.getUserName()).thenReturn("alice");
            assertThat(first.getCurrentRoleNames()).as("without a snapshot, no roles").isEmpty();
        }
        finally {
            runtime.stop();
        }
    }

    @Test
    void aSessionFailsWhenTheAgentCannotStart()
    {
        GrantForgeHiveAuthorizerFactory factory = new GrantForgeHiveAuthorizerFactory(new HiveAgentRuntime(), HashMap::new);
        assertThatThrownBy(() -> factory.createHiveAuthorizer(null, null, mock(HiveAuthenticationProvider.class), null))
                .isInstanceOf(HiveAuthzPluginException.class).hasMessageContaining("cannot start the Hive agent")
                .hasMessageContaining("grantforge.token.file");
        Map<String, String> unreadable = Map.of("grantforge.token.file", temporary.resolve("missing").toString());
        GrantForgeHiveAuthorizerFactory missing = new GrantForgeHiveAuthorizerFactory(new HiveAgentRuntime(), () -> unreadable);
        assertThatThrownBy(() -> missing.createHiveAuthorizer(null, null, mock(HiveAuthenticationProvider.class), null))
                .isInstanceOf(HiveAuthzPluginException.class);
    }

    @Test
    void readsTheServersSettingsWithSystemPropertiesWinning()
    {
        Properties system = new Properties();
        system.setProperty("grantforge.instance", "from-system");
        system.setProperty("java.version", "17");
        List<Map.Entry<String, String>> conf = List.of(new AbstractMap.SimpleEntry<>("grantforge.instance", "from-file"),
                new AbstractMap.SimpleEntry<>("grantforge.cache.dir", "/var/cache"), new AbstractMap.SimpleEntry<>("hive.server2.port", "10000"));
        assertThat(GrantForgeHiveAuthorizerFactory.settings(conf, system)).containsExactlyInAnyOrderEntriesOf(
                Map.of("grantforge.instance", "from-system", "grantforge.cache.dir", "/var/cache"));
    }

    @Test
    void readsTheConfigurationFilesOfTheServer()
    {
        System.setProperty("grantforge.test.marker", "set");
        try {
            assertThat(GrantForgeHiveAuthorizerFactory.serverSettings()).containsEntry("grantforge.test.marker", "set");
            assertThat(new HiveConf().get("grantforge.test.marker")).as("a session's configuration is not read").isNull();
        }
        finally {
            System.clearProperty("grantforge.test.marker");
        }
    }

    @Test
    void knowsItsVersion()
    {
        assertThat(GrantForgeHiveAuthorizerFactory.version()).isNotBlank().isNotEqualTo("unknown").doesNotContain("${");
        assertThat(new GrantForgeHiveAuthorizerFactory()).isNotNull();
    }
}
