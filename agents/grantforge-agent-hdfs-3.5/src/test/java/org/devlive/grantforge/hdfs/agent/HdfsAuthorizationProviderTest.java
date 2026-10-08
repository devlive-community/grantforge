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
import org.apache.hadoop.util.VersionInfo;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.hdfs.common.HdfsAgentRuntime;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsAuthorizationProviderTest
{
    @Test
    void loadsThisAdaptersMetadataAndCreatesItsExactNativeBridge()
    {
        Properties build = HdfsAgentCompatibility.load(HdfsAuthorizationProvider.class.getResourceAsStream(HdfsAgentCompatibility.RESOURCE));
        assertThat(build.getProperty("hadoop.line")).isEqualTo("3.5");
        assertThat(build.getProperty("spi.family")).isEqualTo("superuser");
        HdfsAgentCompatibility.verify(build);
        assertThat(HdfsAgentCompatibility.agentVersion(build)).endsWith("-hadoop-" + VersionInfo.getVersion()).doesNotContain("${");
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider();
        assertThat(provider.getExternalAccessControlEnforcer(null)).isExactlyInstanceOf(HdfsAccessControlEnforcer.class);
    }

    @Test
    void thinFactoryRetainsConfigurationAndLifecycleFailClosedGates() throws IOException
    {
        HdfsAgentRuntime runtime = mock(HdfsAgentRuntime.class);
        when(runtime.settings()).thenReturn(AgentSettings.builder().server(URI.create("https://grantforge.example.com/")).token("token")
                .instance("namenode-test").cacheDirectory(Paths.get("target/test-cache")).build());
        when(runtime.authorizer()).thenReturn(mock(HdfsAuthorizer.class));
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(() -> runtime);
        AccessControlEnforcer enforcer = provider.getExternalAccessControlEnforcer(mock(AccessControlEnforcer.class));
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> check(enforcer)).isInstanceOf(AccessControlException.class).hasMessageContaining("not running");
        Configuration configuration = new Configuration(false);
        configuration.setBoolean("grantforge.hdfs.native.fallback", true);
        provider.setConf(configuration);
        provider.start();
        try {
            verify(runtime).start(any(), anyString());
            assertThatThrownBy(() -> provider.setConf(new Configuration(false))).isInstanceOf(IllegalStateException.class);
            check(enforcer);
        }
        finally {
            provider.stop();
        }
        assertThatThrownBy(() -> check(enforcer)).isInstanceOf(AccessControlException.class).hasMessageContaining("not running");
        verify(runtime).stop();
    }

    @SuppressWarnings("deprecation")
    private static void check(AccessControlEnforcer enforcer) throws AccessControlException
    {
        enforcer.checkPermission("hdfs", "supergroup", UserGroupInformation.createUserForTesting("alice", new String[0]),
                new INodeAttributes[0], new INode[0], new byte[0][], 19, "/", -1, false, null, null, FsAction.READ, null, false);
    }
}
