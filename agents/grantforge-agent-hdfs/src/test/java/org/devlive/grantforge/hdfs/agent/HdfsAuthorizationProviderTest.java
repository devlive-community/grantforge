// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.security.AccessControlException;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsAuthorizationProviderTest
{
    @TempDir
    Path temporary;

    @Test
    void startsOnceStopsOnceAndCanRestartWithANewConfiguration() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        AtomicInteger starts = new AtomicInteger();
        AtomicReference<AgentSettings> received = new AtomicReference<>();
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(settings -> {
            starts.incrementAndGet();
            received.set(settings);
            return agent;
        });
        Configuration configuration = HdfsAgentSettingsTest.configuration(temporary);
        provider.setConf(configuration);
        assertThat(provider.getConf()).isNotSameAs(configuration);
        assertThat(requireNonNull(provider.getConf()).get("grantforge.hdfs.instance")).isEqualTo("namenode-1:8020");
        provider.start();
        provider.start();
        assertThat(starts).hasValue(1);
        assertThat(requireNonNull(received.get()).instance()).isEqualTo("namenode-1:8020");
        assertThatThrownBy(() -> provider.setConf(new Configuration(false))).isInstanceOf(IllegalStateException.class);
        provider.stop();
        provider.stop();
        verify(agent).close();
        provider.setConf(configuration);
        provider.start();
        provider.stop();
        assertThat(starts).hasValue(2);
        verify(agent, times(2)).close();
    }

    @Test
    void configurationCopiesPreventExternalMutationOfAgentSettings() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        AtomicReference<AgentSettings> received = new AtomicReference<>();
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(settings -> {
            received.set(settings);
            return agent;
        });
        assertThat(provider.getConf()).isNull();
        Configuration configuration = HdfsAgentSettingsTest.configuration(temporary);
        provider.setConf(configuration);
        configuration.set("grantforge.hdfs.instance", "mutated-input");
        Configuration returned = requireNonNull(provider.getConf());
        assertThat(returned.get("grantforge.hdfs.instance")).isEqualTo("namenode-1:8020");
        returned.set("grantforge.hdfs.instance", "mutated-getter");
        assertThat(requireNonNull(provider.getConf()).get("grantforge.hdfs.instance")).isEqualTo("namenode-1:8020");
        provider.start();
        assertThat(requireNonNull(received.get()).instance()).isEqualTo("namenode-1:8020");
        provider.stop();
    }

    @Test
    void retainsNativeAttributesAndProvidesTheContextApiForHadoopsStartupProbe()
    {
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider();
        INodeAttributes attributes = mock(INodeAttributes.class);
        assertThat(provider.getAttributes(new String[] {"data"}, attributes)).isSameAs(attributes);
        assertThat(provider.getExternalAccessControlEnforcer(null)).isInstanceOf(HdfsAccessControlEnforcer.class);
    }

    @Test
    void failsToStartWithoutValidConfiguration() throws IOException
    {
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider();
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class);
        provider.setConf(new Configuration(false));
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class);
        Configuration configuration = HdfsAgentSettingsTest.configuration(temporary);
        configuration.set("grantforge.hdfs.token.file", temporary.resolve("missing").toString());
        provider.setConf(configuration);
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsWrongServiceTypeEvenWhenNativeFallbackIsEnabled() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        Snapshot wrong = Snapshot.parse("""
                {"format":1,"service":"hive-service","serviceType":"hive","serviceEnabled":true,"policyVersion":1,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                   "accessTypes":[{"name":"read","impliedGrants":[]}],"conditions":[]},
                 "policies":[],"roles":{},"groups":{}}
                """.getBytes(StandardCharsets.UTF_8), Map.of());
        when(agent.snapshot()).thenReturn(wrong);
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(settings -> agent);
        Configuration configuration = HdfsAgentSettingsTest.configuration(temporary);
        configuration.setBoolean("grantforge.hdfs.native.fallback", true);
        provider.setConf(configuration);
        provider.start();

        AccessControlEnforcer enforcer = provider.getExternalAccessControlEnforcer(mock(AccessControlEnforcer.class));
        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("could not evaluate");
        verify(agent).record(any());
        provider.stop();
    }

    @Test
    void rejectsPermissionChecksBeforeStartAndAfterStop() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(settings -> agent);
        AccessControlEnforcer enforcer = provider.getExternalAccessControlEnforcer(mock(AccessControlEnforcer.class));
        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class);
        provider.setConf(HdfsAgentSettingsTest.configuration(temporary));
        provider.start();
        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.serviceType()).thenReturn("hdfs");
        AgentDecision allowed = HdfsAccessControlEnforcerTest.decision("ALLOWED");
        when(snapshot.decide(any())).thenReturn(allowed);
        when(agent.snapshot()).thenReturn(snapshot);
        enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ));
        provider.stop();
        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class);
    }

    @Test
    void cannotCombineTraversalAndReadGrantsFromDifferentSnapshotVersions() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        Snapshot traversal = allowSnapshot(1, "execute");
        Snapshot read = allowSnapshot(2, "read");
        when(agent.snapshot()).thenReturn(traversal, traversal, read);
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(settings -> agent);
        provider.setConf(HdfsAgentSettingsTest.configuration(temporary));
        provider.start();
        AccessControlEnforcer enforcer = provider.getExternalAccessControlEnforcer(mock(AccessControlEnforcer.class));

        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("read");
        verify(agent).snapshot();
        provider.stop();
    }

    @Test
    void appliesTheNextSnapshotOnTheNextAuthorizationCallback() throws Exception
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        Snapshot first = mock(Snapshot.class);
        Snapshot second = mock(Snapshot.class);
        AtomicReference<Snapshot> published = new AtomicReference<>(first);
        when(agent.snapshot()).thenAnswer(call -> published.get());
        when(first.serviceType()).thenReturn("hdfs");
        AgentDecision allowed = HdfsAccessControlEnforcerTest.decision("ALLOWED");
        when(first.decide(any(AccessRequest.class))).thenAnswer(call -> {
            published.set(second);
            return allowed;
        });
        when(second.serviceType()).thenReturn("hdfs");
        AgentDecision denied = HdfsAccessControlEnforcerTest.decision("DENIED");
        when(second.decide(any(AccessRequest.class))).thenReturn(denied);
        HdfsAuthorizationProvider provider = new HdfsAuthorizationProvider(settings -> agent);
        provider.setConf(HdfsAgentSettingsTest.configuration(temporary));
        provider.start();
        AccessControlEnforcer enforcer = provider.getExternalAccessControlEnforcer(mock(AccessControlEnforcer.class));

        enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ));
        verify(agent).snapshot();
        verify(second, never()).decide(any(AccessRequest.class));
        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(HdfsAccessControlEnforcerTest.context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("DENIED");
        verify(agent, times(2)).snapshot();
        provider.stop();
    }

    private static Snapshot allowSnapshot(long version, String access)
    {
        return Snapshot.parse("""
                {"format":1,"service":"cluster","serviceType":"hdfs","serviceEnabled":true,"policyVersion":%d,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                   "accessTypes":[{"name":"read","impliedGrants":[]},{"name":"write","impliedGrants":[]},
                     {"name":"execute","impliedGrants":[]}],"conditions":[]},
                 "policies":[{"id":"1","type":"ACCESS","name":"allow","priority":"NORMAL","document":{
                   "resources":{"path":{"values":["/"],"excludes":false,"recursive":true}},
                   "allow":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["%s"]}]}}],
                 "roles":{},"groups":{}}
                """.formatted(version, access).getBytes(StandardCharsets.UTF_8), Map.of());
    }
}
