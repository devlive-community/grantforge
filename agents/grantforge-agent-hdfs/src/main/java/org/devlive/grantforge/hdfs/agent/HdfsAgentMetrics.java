// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.metrics2.MetricsCollector;
import org.apache.hadoop.metrics2.MetricsRecordBuilder;
import org.apache.hadoop.metrics2.MetricsSource;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hadoop.metrics2.lib.MetricsRegistry;
import org.apache.hadoop.metrics2.lib.MutableCounterLong;
import org.apache.hadoop.metrics2.lib.MutableGaugeInt;
import org.apache.hadoop.metrics2.lib.MutableGaugeLong;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The agent's counters and gauges in Hadoop's metrics system, so they reach the same JMX and monitoring sinks as
 * the NameNode's own dfs metrics: {@code Hadoop:service=NameNode,name=GrantForgeHdfsAgent}. Registration failures
 * only cost the metrics: enforcement never depends on them.
 */
@Metrics(context = "dfs", about = "GrantForge HDFS NameNode agent")
final class HdfsAgentMetrics
        implements MetricsSource
{
    private static final Logger LOG = Logger.getLogger(HdfsAgentMetrics.class.getName());
    static final String SOURCE_NAME = "GrantForgeHdfsAgent";

    private final MetricsRegistry registry = new MetricsRegistry(SOURCE_NAME);
    private final MutableCounterLong callbacks = registry.newCounter("Callbacks",
            "Authorization callbacks the agent enforced", 0L);
    private final MutableCounterLong superuserCallbacks = registry.newCounter("SuperuserCallbacks",
            "Superuser authorization callbacks the agent enforced", 0L);
    private final MutableCounterLong nativeDenies = registry.newCounter("NativeDenies",
            "Access attempts Hadoop rejected before the agent ran", 0L);
    private final MutableCounterLong failures = registry.newCounter("EvaluationFailures",
            "Callbacks that failed closed because policy evaluation threw", 0L);
    private final MutableCounterLong allowed = registry.newCounter("DecisionsAllowed",
            "Permissions a policy allowed", 0L);
    private final MutableCounterLong denied = registry.newCounter("DecisionsDenied",
            "Permissions a policy denied", 0L);
    private final MutableCounterLong undetermined = registry.newCounter("DecisionsUndetermined",
            "Permissions no policy decided; denied unless native fallback is enabled", 0L);
    private final MutableCounterLong missingSnapshots = registry.newCounter("MissingSnapshots",
            "Callbacks served while no verified policy snapshot existed", 0L);
    private final MutableGaugeLong snapshotVersion = registry.newGauge("SnapshotVersion",
            "Policy version of the snapshot in use, 0 when there is none", 0L);
    private final MutableGaugeLong queuedEvents = registry.newGauge("QueuedEvents",
            "Access events waiting in memory to be shipped", 0L);
    private final MutableGaugeLong droppedEvents = registry.newGauge("DroppedEvents",
            "Access events dropped because the queue or the spool was full", 0L);
    private final MutableGaugeInt serverReachable = registry.newGauge("ServerReachable",
            "1 when the last call to the policy server succeeded, else 0", 0);

    HdfsAgentMetrics(AgentSettings settings)
    {
        registry.tag("instance", "The GrantForge data service instance this agent reports for", settings.instance());
        registry.tag("agentVersion", "The version of the deployed agent jar", settings.agentVersion());
    }

    /** Registers the source with the NameNode's metrics system; returns {@code null} when that is unavailable. */
    static @Nullable HdfsAgentMetrics register(AgentSettings settings)
    {
        try {
            HdfsAgentMetrics metrics = new HdfsAgentMetrics(settings);
            DefaultMetricsSystem.instance().register(SOURCE_NAME, "GrantForge HDFS NameNode agent", metrics);
            return metrics;
        }
        catch (RuntimeException unavailable) {
            LOG.log(Level.WARNING, "GrantForge agent metrics are unavailable; authorization continues without them",
                    unavailable);
            return null;
        }
    }

    /** Removes the source again; safe to call without a prior registration. */
    static void unregister()
    {
        try {
            DefaultMetricsSystem.instance().unregisterSource(SOURCE_NAME);
        }
        catch (RuntimeException unavailable) {
            LOG.log(Level.FINE, "GrantForge agent metrics were not registered", unavailable);
        }
    }

    void callback()
    {
        callbacks.incr();
    }

    void superuserCallback()
    {
        superuserCallbacks.incr();
    }

    void nativeDeny()
    {
        nativeDenies.incr();
    }

    void failure()
    {
        failures.incr();
    }

    void decision(AgentDecision decision)
    {
        switch (decision.outcome()) {
            case ALLOWED -> allowed.incr();
            case DENIED -> denied.incr();
            case NOT_DETERMINED -> undetermined.incr();
        }
    }

    void missingSnapshot()
    {
        missingSnapshots.incr();
    }

    /** Refreshes the gauges from the agent the callback just used. */
    void snapshot(GrantForgeAgent agent, Snapshot snapshot)
    {
        snapshotVersion.set(snapshot.policyVersion());
        queuedEvents.set(agent.queuedEvents());
        droppedEvents.set(agent.droppedEvents());
        serverReachable.set(agent.serverReachable() ? 1 : 0);
    }

    @Override
    public void getMetrics(MetricsCollector collector, boolean all)
    {
        MetricsRecordBuilder builder = collector.addRecord(SOURCE_NAME).setContext("dfs");
        registry.snapshot(builder, all);
    }

    long value(String name)
    {
        return switch (name) {
            case "Callbacks" -> callbacks.value();
            case "SuperuserCallbacks" -> superuserCallbacks.value();
            case "NativeDenies" -> nativeDenies.value();
            case "EvaluationFailures" -> failures.value();
            case "DecisionsAllowed" -> allowed.value();
            case "DecisionsDenied" -> denied.value();
            case "DecisionsUndetermined" -> undetermined.value();
            case "MissingSnapshots" -> missingSnapshots.value();
            case "SnapshotVersion" -> snapshotVersion.value();
            case "QueuedEvents" -> queuedEvents.value();
            case "DroppedEvents" -> droppedEvents.value();
            case "ServerReachable" -> serverReachable.value();
            default -> throw new IllegalArgumentException("no metric " + name);
        };
    }
}
