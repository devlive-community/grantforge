// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.hdfs.common.HdfsAgentRuntime;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsAuthorizationProviderTest
{
    private static HdfsAgentRuntime runtime()
    {
        HdfsAgentRuntime runtime = mock(HdfsAgentRuntime.class);
        when(runtime.settings()).thenReturn(AgentSettings.builder().server(URI.create("https://grantforge.example.com/")).token("token")
                .instance("namenode-test").cacheDirectory(Paths.get("target/test-cache")).build());
        when(runtime.authorizer()).thenReturn(mock(HdfsAuthorizer.class));
        return runtime;
    }

    @Test
    void copiesConfigurationResolvesSubstitutionsAndOwnsAnIdempotentLifecycle() throws IOException
    {
        HdfsAgentRuntime runtime = runtime();
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(() -> runtime);
        Configuration configuration = new Configuration(false);
        configuration.set("grantforge.url", "https://grantforge.example.com/");
        configuration.set("grantforge.hdfs.server.url", "${grantforge.url}");
        configuration.set("dfs.permissions.enabled", "true");
        provider.setConf(configuration);
        configuration.set("grantforge.url", "https://changed.example.com/");
        Configuration copy = provider.getConf();
        assertThat(copy).isNotNull();
        copy.set("grantforge.url", "https://another.example.com/");
        provider.start();
        try {
            provider.start();
            ArgumentCaptor<Map<String, String>> settings = ArgumentCaptor.forClass(Map.class);
            verify(runtime).start(settings.capture(), anyString());
            assertThat(settings.getValue()).containsEntry("grantforge.hdfs.server.url", "https://grantforge.example.com/")
                    .containsEntry("dfs.permissions.enabled", "true").doesNotContainKey("grantforge.url");
            assertThatThrownBy(() -> provider.setConf(new Configuration(false))).isInstanceOf(IllegalStateException.class);
        }
        finally {
            provider.stop();
        }
        provider.stop();
        verify(runtime).stop();
        provider.setConf(new Configuration(false));
        provider.start();
        provider.stop();
        verify(runtime, times(2)).stop();
    }

    @Test
    void retainsNativeAttributesAndFailsClosedBeforeStartAndAfterStop() throws IOException
    {
        HdfsAgentRuntime runtime = runtime();
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(() -> runtime);
        INodeAttributes attributes = mock(INodeAttributes.class);
        assertThat(provider.getAttributes(new String[] {"data"}, attributes)).isSameAs(attributes);
        AccessControlEnforcer enforcer = provider.getExternalAccessControlEnforcer(mock(AccessControlEnforcer.class));
        assertThatThrownBy(() -> check(enforcer)).isInstanceOf(AccessControlException.class).hasMessageContaining("not running");
        provider.setConf(new Configuration(false));
        provider.start();
        check(enforcer);
        provider.stop();
        assertThatThrownBy(() -> check(enforcer)).isInstanceOf(AccessControlException.class).hasMessageContaining("not running");
    }

    @Test
    void rejectsMissingConfigurationAndCleansUpAFailedStartup() throws IOException
    {
        assertThatThrownBy(new HdfsAuthorizationProvider()::start).isInstanceOf(IllegalStateException.class);
        HdfsAgentRuntime runtime = runtime();
        doThrow(new IOException("token cannot be read")).when(runtime).start(any(), anyString());
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(() -> runtime);
        provider.setConf(new Configuration(false));
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IOException.class);
        verify(runtime).stop();
    }

    @SuppressWarnings("deprecation")
    private static void check(AccessControlEnforcer enforcer) throws AccessControlException
    {
        enforcer.checkPermission("hdfs", "supergroup", UserGroupInformation.createUserForTesting("alice", new String[0]),
                new INodeAttributes[0], new INode[0], new byte[0][], 19, "/", -1, false, null, null, FsAction.READ, null, false);
    }
}
