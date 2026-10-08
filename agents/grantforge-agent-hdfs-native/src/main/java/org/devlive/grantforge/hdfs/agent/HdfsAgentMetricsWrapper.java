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
import org.devlive.grantforge.hdfs.common.HdfsMetrics;
import org.jspecify.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The agent's counters and gauges in Hadoop's metrics system, so they reach the same JMX and monitoring sinks as
 * the NameNode's own dfs metrics: {@code Hadoop:service=NameNode,name=GrantForgeHdfsAgent}. Registration failures
 * only cost the metrics: enforcement never depends on them.
 */
@Metrics(context = "dfs", about = "GrantForge HDFS NameNode agent")
final class HdfsAgentMetricsWrapper
        implements MetricsSource, HdfsMetrics
{
    private static final Logger LOG = Logger.getLogger(HdfsAgentMetricsWrapper.class.getName());
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

    HdfsAgentMetricsWrapper(AgentSettings settings)
    {
        registry.tag("instance", "The GrantForge data service instance this agent reports for", settings.instance());
        registry.tag("agentVersion", "The version of the deployed agent jar", settings.agentVersion());
    }

    /** Registers the source with the NameNode's metrics system; returns {@code null} when that is unavailable. */
    static @Nullable HdfsAgentMetricsWrapper register(AgentSettings settings)
    {
        try {
            HdfsAgentMetricsWrapper metrics = new HdfsAgentMetricsWrapper(settings);
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

    @Override
    public void callback()
    {
        callbacks.incr();
    }

    @Override
    public void superuserCallback()
    {
        superuserCallbacks.incr();
    }

    @Override
    public void nativeDeny()
    {
        nativeDenies.incr();
    }

    @Override
    public void failure()
    {
        failures.incr();
    }

    @Override
    public void decision(AgentDecision decision)
    {
        switch (decision.outcome()) {
            case ALLOWED:
                allowed.incr();
                break;
            case DENIED:
                denied.incr();
                break;
            case NOT_DETERMINED:
                undetermined.incr();
                break;
        }
    }

    @Override
    public void missingSnapshot()
    {
        missingSnapshots.incr();
    }

    @Override
    public void snapshot(long version, long queued, long dropped, boolean reachable)
    {
        snapshotVersion.set(version);
        queuedEvents.set(queued);
        droppedEvents.set(dropped);
        serverReachable.set(reachable ? 1 : 0);
    }

    @Override
    public void getMetrics(MetricsCollector collector, boolean all)
    {
        MetricsRecordBuilder builder = collector.addRecord(SOURCE_NAME).setContext("dfs");
        registry.snapshot(builder, all);
    }

    long value(String name)
    {
        switch (name) {
            case "Callbacks": return callbacks.value();
            case "SuperuserCallbacks": return superuserCallbacks.value();
            case "NativeDenies": return nativeDenies.value();
            case "EvaluationFailures": return failures.value();
            case "DecisionsAllowed": return allowed.value();
            case "DecisionsDenied": return denied.value();
            case "DecisionsUndetermined": return undetermined.value();
            case "MissingSnapshots": return missingSnapshots.value();
            case "SnapshotVersion": return snapshotVersion.value();
            case "QueuedEvents": return queuedEvents.value();
            case "DroppedEvents": return droppedEvents.value();
            case "ServerReachable": return serverReachable.value();
            default: throw new IllegalArgumentException("no metric " + name);
        }
    }
}
