// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Hadoop 3.5.0 NameNode attribute provider. Install its jar in the NameNode classpath and select this class with
 * {@code dfs.namenode.inode.attributes.provider.class}. Native inode attributes and HDFS checks remain authoritative;
 * GrantForge policies add restrictions. Settings are read from {@code hdfs-site.xml} under {@code grantforge.hdfs.}.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class HdfsAuthorizationProvider
        extends INodeAttributeProvider
        implements Configurable
{
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Function<AgentSettings, GrantForgeAgent> agentFactory;
    private @Nullable Configuration configuration;
    private volatile @Nullable GrantForgeAgent agent;
    private volatile boolean nativeFallback;

    /** Creates the provider; Hadoop injects the NameNode configuration before starting it. */
    public HdfsAuthorizationProvider()
    {
        this(settings -> GrantForgeAgent.start(settings, Map.of()));
    }

    HdfsAuthorizationProvider(Function<AgentSettings, GrantForgeAgent> agentFactory)
    {
        this.agentFactory = agentFactory;
    }

    @Override
    public void setConf(Configuration value)
    {
        lifecycle.lock();
        try {
            if (agent != null) {
                throw new IllegalStateException("stop the HDFS agent before changing its configuration");
            }
            configuration = new Configuration(value);
        }
        finally {
            lifecycle.unlock();
        }
    }

    @Override
    public @Nullable Configuration getConf()
    {
        lifecycle.lock();
        try {
            Configuration settings = configuration;
            return settings == null ? null : new Configuration(settings);
        }
        finally {
            lifecycle.unlock();
        }
    }

    @Override
    public void start()
    {
        lifecycle.lock();
        try {
            if (agent != null) {
                return;
            }
            Configuration settings = configuration;
            if (settings == null) {
                throw new IllegalStateException("the NameNode did not supply the HDFS agent configuration");
            }
            try {
                HdfsAgentSettings parsed = HdfsAgentSettings.read(settings);
                nativeFallback = parsed.nativeFallback();
                agent = agentFactory.apply(parsed.agent());
            }
            catch (IOException | IllegalArgumentException invalid) {
                throw new IllegalStateException("cannot configure the GrantForge HDFS agent", invalid);
            }
        }
        finally {
            lifecycle.unlock();
        }
    }

    @Override
    // Only this lifecycle method owns closing the agent. Clearing its published reference makes subsequent checks fail closed.
    @SuppressWarnings({"PMD.CloseResource", "PMD.NullAssignment"})
    public void stop()
    {
        lifecycle.lock();
        try {
            GrantForgeAgent running = agent;
            agent = null;
            if (running != null) {
                running.close();
            }
        }
        finally {
            lifecycle.unlock();
        }
    }

    @Override
    public INodeAttributes getAttributes(String[] pathElements, INodeAttributes inode)
    {
        return inode;
    }

    @Override
    public AccessControlEnforcer getExternalAccessControlEnforcer(@Nullable AccessControlEnforcer defaultEnforcer)
    {
        // Hadoop probes the returned class with a null defaultEnforcer during startup, before any permission check.
        return new HdfsAccessControlEnforcer(defaultEnforcer, this::decisions, this::record, () -> nativeFallback);
    }

    // Borrow the provider's shared running agent; stop() alone owns its lifetime and closes it.
    @SuppressWarnings("PMD.CloseResource")
    private Function<AccessRequest, AgentDecision> decisions()
    {
        GrantForgeAgent running = agent;
        if (running == null) {
            throw new IllegalStateException("the GrantForge HDFS agent is not running");
        }
        Snapshot snapshot = running.snapshot();
        if (snapshot == null) {
            return request -> AgentDecision.withoutSnapshot();
        }
        if (!"hdfs".equals(snapshot.serviceType())) {
            throw new IllegalStateException("the NameNode agent token is bound to a service whose type is not hdfs");
        }
        return snapshot::decide;
    }

    // Audit callbacks borrow the shared agent; they must not close the resource owned by stop().
    @SuppressWarnings("PMD.CloseResource")
    private void record(org.devlive.grantforge.agent.AccessEvent event)
    {
        GrantForgeAgent running = agent;
        if (running != null) {
            running.record(event);
        }
    }
}
