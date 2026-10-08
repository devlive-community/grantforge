// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.devlive.grantforge.hdfs.common.HdfsAgentRuntime;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Version-specific NameNode entry point, compiled separately against each adapter's native Hadoop SPI. Configure
 * {@code dfs.namenode.inode.attributes.provider.class} with this class and install exactly one numbered adapter jar.
 * The selected artifact verifies its runtime compatibility before starting the shared signed-policy agent.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class HdfsAuthorizationProvider
        extends INodeAttributeProvider
        implements Configurable
{
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Supplier<HdfsAgentRuntime> runtimes;
    private @Nullable Configuration configuration;
    private volatile @Nullable HdfsAgentRuntime runtime;

    /** Creates the provider; Hadoop supplies its NameNode configuration before calling {@link #start()}. */
    public HdfsAuthorizationProvider()
    {
        this(HdfsAgentRuntime::new);
    }

    HdfsAuthorizationProvider(Supplier<HdfsAgentRuntime> runtimes)
    {
        this.runtimes = runtimes;
    }

    @Override
    public void setConf(Configuration value)
    {
        lifecycle.lock();
        try {
            if (runtime != null) {
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
    // Startup publishes the runtime only after it succeeds. The provider's stop() owns its lifetime thereafter.
    @SuppressWarnings("PMD.CloseResource")
    public void start()
    {
        lifecycle.lock();
        try {
            if (runtime != null) {
                return;
            }
            Configuration settings = configuration;
            if (settings == null) {
                throw new IllegalStateException("the NameNode did not supply the HDFS agent configuration");
            }
            HdfsAgentCompatibility.verify();
            HdfsAgentRuntime running = runtimes.get();
            try {
                running.start(resolvedSettings(settings), HdfsAgentCompatibility.agentVersion());
                running.setMetrics(HdfsAgentMetricsWrapper.register(running.settings()));
                runtime = running;
            }
            catch (IOException | RuntimeException invalid) {
                running.stop();
                throw new IllegalStateException("cannot configure the GrantForge HDFS agent", invalid);
            }
        }
        finally {
            lifecycle.unlock();
        }
    }

    @Override
    // Clearing the published runtime closes the authorization gate before shutting down its background work.
    @SuppressWarnings({"PMD.CloseResource", "PMD.NullAssignment"})
    public void stop()
    {
        lifecycle.lock();
        try {
            HdfsAgentRuntime running = runtime;
            runtime = null;
            if (running != null) {
                running.stop();
                HdfsAgentMetricsWrapper.unregister();
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
        // Modern NameNodes probe the returned concrete class for a declared context callback using a null delegate.
        return new HdfsAccessControlEnforcer(defaultEnforcer, this::authorizer);
    }

    // Callback authorizers borrow the running runtime; only stop() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    private HdfsAuthorizer authorizer()
    {
        HdfsAgentRuntime running = runtime;
        if (running == null) {
            throw new IllegalStateException("the GrantForge HDFS agent is not running");
        }
        return running.authorizer();
    }

    private static Map<String, String> resolvedSettings(Configuration settings)
    {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : settings) {
            String name = entry.getKey();
            if (name.startsWith("grantforge.hdfs.") || "dfs.permissions.enabled".equals(name)
                    || "dfs.namenode.inode.attributes.provider.bypass.users".equals(name)) {
                // Configuration.get resolves substitutions while iterator values may still contain ${...} expressions.
                String value = settings.get(name);
                if (value != null) {
                    values.put(name, value);
                }
            }
        }
        return values;
    }
}
