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
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Shared NameNode lifecycle and native inode attributes, compiled against the oldest supported permission SPI.
 * Numbered adapter entry points supply their own build metadata and callback implementations. The selected artifact
 * verifies its runtime compatibility before starting the shared signed-policy agent.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public abstract class HdfsNativeAuthorizationProvider
        extends INodeAttributeProvider
        implements Configurable
{
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Supplier<HdfsAgentRuntime> runtimes;
    private final Properties build;
    private @Nullable Configuration configuration;
    private volatile @Nullable HdfsAgentRuntime runtime;

    /**
     * Creates the shared provider using metadata loaded by its numbered adapter's own class loader.
     *
     * @param build the adapter metadata, defensively copied including effective string defaults
     * @param runtimes supplies the shared signed-policy runtime owned by this provider
     */
    protected HdfsNativeAuthorizationProvider(Properties build, Supplier<HdfsAgentRuntime> runtimes)
    {
        this(new Setup(build, runtimes));
    }

    private HdfsNativeAuthorizationProvider(Setup setup)
    {
        runtimes = setup.runtimes;
        build = setup.build;
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
            HdfsAgentCompatibility.verify(build);
            HdfsAgentRuntime running = runtimes.get();
            try {
                running.start(resolvedSettings(settings), HdfsAgentCompatibility.agentVersion(build));
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
        return createEnforcer(defaultEnforcer, this::authorizer);
    }

    /**
     * Creates the numbered adapter's enforcer, declaring only callbacks available on its native Hadoop SPI.
     *
     * @param defaultEnforcer native permission checks, or null during the NameNode's startup API probe
     * @param authorizers supplies a live policy overlay for each callback
     * @return the version-specific native enforcer
     */
    protected abstract AccessControlEnforcer createEnforcer(@Nullable AccessControlEnforcer defaultEnforcer,
            Supplier<HdfsAuthorizer> authorizers);

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

    // Validate before the provider's superclass constructor starts: a failed extensible constructor must never
    // leave a partially initialized provider accessible through a subclass finalizer. This holder is final.
    private static final class Setup
    {
        final Supplier<HdfsAgentRuntime> runtimes;
        final Properties build;

        Setup(Properties build, Supplier<HdfsAgentRuntime> runtimes)
        {
            this.runtimes = requireNonNull(runtimes, "runtimes");
            this.build = new Properties();
            for (String name : requireNonNull(build, "build").stringPropertyNames()) {
                this.build.setProperty(name, requireNonNull(build.getProperty(name), name));
            }
        }
    }
}
