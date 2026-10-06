// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.metrics2.MetricsCollector;
import org.apache.hadoop.metrics2.MetricsInfo;
import org.apache.hadoop.metrics2.MetricsRecordBuilder;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsAgentMetricsTest
{
    private final AgentSettings settings = AgentSettings.builder().server(URI.create("http://127.0.0.1:9999/")).token("t")
            .instance("it-namenode").agentVersion("2026.1.0-hadoop-3.5.0").cacheDirectory(Path.of("/tmp/agent")).build();

    @Test
    void countsCallbacksNativeDeniesFailuresAndDecisionOutcomes()
    {
        HdfsAgentMetrics metrics = new HdfsAgentMetrics(settings);
        metrics.callback();
        metrics.callback();
        metrics.superuserCallback();
        metrics.nativeDeny();
        metrics.failure();
        metrics.decision(decision(AgentDecision.Outcome.ALLOWED));
        metrics.decision(decision(AgentDecision.Outcome.DENIED));
        metrics.decision(decision(AgentDecision.Outcome.NOT_DETERMINED));
        metrics.missingSnapshot();

        assertThat(metrics.value("Callbacks")).isEqualTo(2);
        assertThat(metrics.value("SuperuserCallbacks")).isEqualTo(1);
        assertThat(metrics.value("NativeDenies")).isEqualTo(1);
        assertThat(metrics.value("EvaluationFailures")).isEqualTo(1);
        assertThat(metrics.value("DecisionsAllowed")).isEqualTo(1);
        assertThat(metrics.value("DecisionsDenied")).isEqualTo(1);
        assertThat(metrics.value("DecisionsUndetermined")).isEqualTo(1);
        assertThat(metrics.value("MissingSnapshots")).isEqualTo(1);
    }

    @Test
    void refreshesTheGaugesFromTheAgentAndSnapshot()
    {
        HdfsAgentMetrics metrics = new HdfsAgentMetrics(settings);
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        when(agent.queuedEvents()).thenReturn(4);
        when(agent.droppedEvents()).thenReturn(2L);
        when(agent.serverReachable()).thenReturn(true);
        Snapshot snapshot = Snapshot.parse(("{'format':1,'service':'cluster','serviceType':'hdfs','serviceEnabled':true,"
                + "'policyVersion':7,'definition':{'resources':[{'name':'path','parent':null,'matcher':'PATH','caseSensitive':true}],"
                + "'accessTypes':[{'name':'read','impliedGrants':[]},{'name':'write','impliedGrants':[]},{'name':'execute','impliedGrants':[]}],"
                + "'conditions':[]},'policies':[],'roles':{},'groups':{}}").replace('\'', '"').getBytes(StandardCharsets.UTF_8), Map.of());

        metrics.snapshot(agent, snapshot);

        assertThat(metrics.value("SnapshotVersion")).isEqualTo(7);
        assertThat(metrics.value("QueuedEvents")).isEqualTo(4);
        assertThat(metrics.value("DroppedEvents")).isEqualTo(2);
        assertThat(metrics.value("ServerReachable")).isEqualTo(1);
    }

    @Test
    void emitsTheSourceIntoACollectorRecordWithoutThrowing()
    {
        HdfsAgentMetrics metrics = new HdfsAgentMetrics(settings);
        metrics.callback();
        MetricsCollector collector = mock(MetricsCollector.class);
        MetricsRecordBuilder builder = mock(MetricsRecordBuilder.class);
        when(collector.addRecord(HdfsAgentMetrics.SOURCE_NAME)).thenReturn(builder);
        when(builder.setContext(any())).thenReturn(builder);
        when(builder.tag(any(MetricsInfo.class), any())).thenReturn(builder);

        metrics.getMetrics(collector, true);

        verify(builder).setContext("dfs");
        verify(builder, atLeastOnce()).addCounter(any(MetricsInfo.class), anyLong());
    }

    @Test
    void unregisterIsSafeWithoutARegistration()
    {
        HdfsAgentMetrics.unregister();
        assertThat(new HdfsAgentMetrics(settings).value("Callbacks")).isZero();
    }

    private static AgentDecision decision(AgentDecision.Outcome outcome)
    {
        AgentDecision decision = mock(AgentDecision.class);
        when(decision.outcome()).thenReturn(outcome);
        return decision;
    }
}
