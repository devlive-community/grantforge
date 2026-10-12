// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.auth.SASLAuthenticationProvider;
import org.apache.zookeeper.server.auth.ServerAuthenticationProvider;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ZooKeeper's {@code sasl} scheme with GrantForge's policies: configured in {@code zoo.cfg} as
 * {@code authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider} in place of
 * ZooKeeper's own {@code SASLAuthenticationProvider}. The ACL entry {@code sasl:grantforge} (with all of {@code cdrwa})
 * is decided by the service's policies for the signed-in principal; every other {@code sasl:<principal>} entry matches as
 * ZooKeeper's own provider matches it. Without a running agent the GrantForge entry refuses.
 */
public final class GrantForgeSaslAuthenticationProvider
        extends ServerAuthenticationProvider
{
    /** The id of the ACL entry GrantForge decides. */
    public static final String MARKER = "grantforge";

    static final Logger LOG = Logger.getLogger(GrantForgeSaslAuthenticationProvider.class.getName());

    private final SASLAuthenticationProvider sasl = new SASLAuthenticationProvider();
    private final @Nullable ZooKeeperAuthorizer authorizer;

    /** Creates the provider ZooKeeper registers, with the agent of this server. */
    public GrantForgeSaslAuthenticationProvider()
    {
        this(configuredAuthorizer());
    }

    GrantForgeSaslAuthenticationProvider(@Nullable ZooKeeperAuthorizer authorizer)
    {
        requireStablePaths();
        this.authorizer = authorizer;
    }

    private static void requireStablePaths()
    {
        if (ZooKeeperServer.isEnableEagerACLCheck()) {
            throw new IllegalStateException("GrantForge requires zookeeper.enableEagerACLCheck=false for consistent create/delete paths");
        }
    }

    private static @Nullable ZooKeeperAuthorizer configuredAuthorizer()
    {
        requireStablePaths();
        return Agent.authorizer();
    }

    @Override
    public String getScheme()
    {
        return "sasl";
    }

    @Override
    public KeeperException.Code handleAuthentication(ServerObjs serverObjs, byte[] authData)
    {
        return sasl.handleAuthentication(serverObjs.getCnxn(), authData);
    }

    @Override
    public boolean matches(ServerObjs serverObjs, MatchValues matchValues)
    {
        if (!MARKER.equals(matchValues.getAclExpr())) {
            return sasl.matches(matchValues.getId(), matchValues.getAclExpr());
        }
        ZooKeeperAuthorizer decide = authorizer;
        return !ZooKeeperServer.isEnableEagerACLCheck() && decide != null
                && decide.authorize(matchValues.getId(), matchValues.getPath(), matchValues.getPerm(), clientIp(serverObjs));
    }

    @Override
    public boolean isAuthenticated()
    {
        return sasl.isAuthenticated();
    }

    @Override
    public boolean isValid(String id)
    {
        return MARKER.equals(id) || sasl.isValid(id);
    }

    // Forwarded writes reach the leader without a client connection; do not substitute the quorum peer's address.
    private static @Nullable String clientIp(ServerObjs serverObjs)
    {
        InetSocketAddress address = serverObjs.getCnxn() == null ? null : serverObjs.getCnxn().getRemoteSocketAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    /** The agent of this server, started once from the {@code grantforge.*} settings and stopped with the process. */
    static final class Agent
    {
        private static final ReentrantLock LOCK = new ReentrantLock();
        private static final Agent STATE = new Agent();

        private @Nullable ZooKeeperAuthorizer authorizer;
        private boolean tried;

        private Agent()
        {
        }

        /**
         * Starts the agent the first time; afterwards, or after a failure, returns what the first time gave.
         *
         * @return the authorizer, or {@code null} when the agent could not start, so the GrantForge entry refuses
         */
        static @Nullable ZooKeeperAuthorizer authorizer()
        {
            LOCK.lock();
            try {
                if (!STATE.tried) {
                    STATE.tried = true;
                    STATE.authorizer = start();
                }
                return STATE.authorizer;
            }
            finally {
                LOCK.unlock();
            }
        }

        /**
         * Puts an authorizer in place of the one the settings would start, for tests running a real server.
         *
         * @param value the authorizer
         */
        static void use(ZooKeeperAuthorizer value)
        {
            LOCK.lock();
            try {
                STATE.tried = true;
                STATE.authorizer = value;
            }
            finally {
                LOCK.unlock();
            }
        }

        // A successful runtime is owned by the process shutdown hook; failure closes it immediately.
        @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.CloseResource"})
        private static @Nullable ZooKeeperAuthorizer start()
        {
            ZooKeeperAgentRuntime runtime = new ZooKeeperAgentRuntime();
            try {
                runtime.start(settings(), version());
            }
            catch (IOException | RuntimeException failed) {
                runtime.stop();
                LOG.log(Level.SEVERE, "The GrantForge ZooKeeper agent could not start; ACL entries sasl:grantforge refuse", failed);
                return null;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop, "grantforge-zookeeper-agent-stop"));
            return runtime.authorizer();
        }

        /** The {@code grantforge.*} settings: from zoo.cfg, which ZooKeeper turns into zookeeper.grantforge.*, or -D. */
        static Map<String, String> settings()
        {
            Map<String, String> values = new HashMap<>();
            Properties system = System.getProperties();
            for (String name : system.stringPropertyNames()) {
                if (name.startsWith("zookeeper." + ZooKeeperAgentRuntime.PREFIX)) {
                    values.put(name.substring("zookeeper.".length()), system.getProperty(name));
                }
            }
            for (String name : system.stringPropertyNames()) {
                if (name.startsWith(ZooKeeperAgentRuntime.PREFIX)) {
                    values.put(name, system.getProperty(name));
                }
            }
            return values;
        }

        private static String version()
        {
            Properties properties = new Properties();
            try (InputStream in = GrantForgeSaslAuthenticationProvider.class.getResourceAsStream(
                    "/META-INF/grantforge/zookeeper-agent-version.properties")) {
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
}
