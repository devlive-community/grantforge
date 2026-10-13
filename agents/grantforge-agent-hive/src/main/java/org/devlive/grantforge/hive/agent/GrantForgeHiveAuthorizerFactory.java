// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.security.HiveAuthenticationProvider;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthorizer;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthorizerFactory;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzPluginException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzSessionContext;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveMetastoreClientFactory;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Creates the GrantForge authorizer of each HiveServer2 session; name it in {@code hive.security.authorization.manager}.
 * The agent starts with the first session and stops with the server. Its {@code grantforge.*} settings are read from the
 * server's own configuration files and system properties, never from a session's, which a client may set.
 */
public final class GrantForgeHiveAuthorizerFactory
        implements HiveAuthorizerFactory
{
    private static final Logger LOG = Logger.getLogger(GrantForgeHiveAuthorizerFactory.class.getName());
    private static final HiveAgentRuntime RUNTIME = new HiveAgentRuntime();

    private final HiveAgentRuntime runtime;
    private final Supplier<Map<String, String>> settings;

    /** Creates the factory HiveServer2 loads, with the agent of the server. */
    public GrantForgeHiveAuthorizerFactory()
    {
        this(RUNTIME, GrantForgeHiveAuthorizerFactory::serverSettings);
    }

    GrantForgeHiveAuthorizerFactory(HiveAgentRuntime runtime, Supplier<Map<String, String>> settings)
    {
        this.runtime = requireNonNull(runtime, "runtime");
        this.settings = requireNonNull(settings, "settings");
    }

    @Override
    // HiveServer2 is no web application: the hook flushes the agent's access events when the server stops.
    @SuppressWarnings("PMD.DoNotUseThreads")
    public HiveAuthorizer createHiveAuthorizer(@Nullable HiveMetastoreClientFactory metastore, @Nullable HiveConf conf,
            HiveAuthenticationProvider authenticator, @Nullable HiveAuthzSessionContext context) throws HiveAuthzPluginException
    {
        try {
            if (runtime.start(settings.get(), version())) {
                Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop, "grantforge-hive-agent-stop"));
                LOG.info("The GrantForge Hive agent started");
            }
        }
        catch (IOException | RuntimeException failed) {
            throw new HiveAuthzPluginException("GrantForge cannot start the Hive agent: " + failed.getMessage(), failed);
        }
        return new GrantForgeHiveAuthorizer(runtime::policies, runtime::record, authenticator, context);
    }

    /**
     * Reads the agent's settings from HiveServer2's configuration files ({@code hive-site.xml} and
     * {@code hiveserver2-site.xml}), then from system properties, which win.
     *
     * @return the {@code grantforge.*} settings
     */
    static Map<String, String> serverSettings()
    {
        return settings(new HiveConf(), System.getProperties());
    }

    /**
     * Collects the agent's settings.
     *
     * @param conf a configuration of the server, not of a session
     * @param system system properties
     * @return the {@code grantforge.*} settings, system properties winning
     */
    static Map<String, String> settings(Iterable<Map.Entry<String, String>> conf, Properties system)
    {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, String> entry : conf) {
            if (entry.getKey().startsWith(HiveAgentRuntime.PREFIX)) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        for (String name : system.stringPropertyNames()) {
            if (name.startsWith(HiveAgentRuntime.PREFIX)) {
                values.put(name, system.getProperty(name));
            }
        }
        return values;
    }

    /**
     * Returns the agent's version.
     *
     * @return the GrantForge version it was built with
     */
    static String version()
    {
        Properties properties = new Properties();
        try (InputStream in = GrantForgeHiveAuthorizerFactory.class.getResourceAsStream("/META-INF/grantforge/hive-agent-version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        }
        catch (IOException unreadable) {
            LOG.log(Level.FINE, "The agent version cannot be read", unreadable);
        }
        return properties.getProperty("grantforge.version", "unknown");
    }
}
