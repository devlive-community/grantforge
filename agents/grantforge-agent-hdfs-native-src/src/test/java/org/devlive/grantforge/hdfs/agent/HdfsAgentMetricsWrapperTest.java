// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.metrics2.MetricsCollector;
import org.apache.hadoop.metrics2.MetricsRecordBuilder;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsAgentMetricsWrapperTest
{
    private static AgentSettings settings()
    {
        return AgentSettings.builder().server(URI.create("https://grantforge.example.com/")).token("test-token")
                .instance("namenode-test").cacheDirectory(Paths.get("target/test-cache")).build();
    }

    @Test
    void exposesNeutralCountersAndRuntimeGaugesThroughNativeMetrics()
    {
        HdfsAgentMetricsWrapper metrics = new HdfsAgentMetricsWrapper(settings());
        metrics.callback();
        metrics.superuserCallback();
        metrics.nativeDeny();
        metrics.failure();
        metrics.missingSnapshot();
        AgentDecision decision = mock(AgentDecision.class);
        for (AgentDecision.Outcome outcome : AgentDecision.Outcome.values()) {
            when(decision.outcome()).thenReturn(outcome);
            metrics.decision(decision);
        }
        metrics.snapshot(42, 8, 3, true);

        for (String name : new String[] {"Callbacks", "SuperuserCallbacks", "NativeDenies", "EvaluationFailures", "MissingSnapshots",
                "DecisionsAllowed", "DecisionsDenied", "DecisionsUndetermined"}) {
            assertThat(metrics.value(name)).as(name).isEqualTo(1);
        }
        assertThat(metrics.value("SnapshotVersion")).isEqualTo(42);
        assertThat(metrics.value("QueuedEvents")).isEqualTo(8);
        assertThat(metrics.value("DroppedEvents")).isEqualTo(3);
        assertThat(metrics.value("ServerReachable")).isEqualTo(1);
        metrics.snapshot(0, 0, 4, false);
        assertThat(metrics.value("ServerReachable")).isZero();
        assertThatThrownBy(() -> metrics.value("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registersExportsAndUnregistersTheNameNodeSource()
    {
        HdfsAgentMetricsWrapper.unregister();
        try {
            HdfsAgentMetricsWrapper metrics = HdfsAgentMetricsWrapper.register(settings());
            assertThat(metrics).isNotNull();
            assertThat(DefaultMetricsSystem.instance().getSource(HdfsAgentMetricsWrapper.SOURCE_NAME)).isSameAs(metrics);
            MetricsCollector collector = mock(MetricsCollector.class);
            MetricsRecordBuilder record = mock(MetricsRecordBuilder.class);
            when(collector.addRecord(HdfsAgentMetricsWrapper.SOURCE_NAME)).thenReturn(record);
            when(record.setContext("dfs")).thenReturn(record);
            metrics.getMetrics(collector, true);
            verify(collector).addRecord(HdfsAgentMetricsWrapper.SOURCE_NAME);
            verify(record).setContext("dfs");
        }
        finally {
            HdfsAgentMetricsWrapper.unregister();
        }
        assertThat(DefaultMetricsSystem.instance().getSource(HdfsAgentMetricsWrapper.SOURCE_NAME)).isNull();
    }
}
