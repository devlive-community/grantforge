// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.sync.AgentRuntime;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A running agent: the signed-policy agent that keeps the Redis service's snapshot and ships access events, the sync that
 * keeps the users' selectors on every node in step with it, and the reader of the nodes' ACL logs. Each runs on a timer;
 * a sync or read that fails is tried again at the next turn.
 */
public final class RedisAgent
        implements AutoCloseable
{
    /** The oldest Redis release the agent supports: the first whose ACL log numbers its entries. Valkey shows its Redis release beside its own. */
    static final int[] MINIMUM = {7, 2};

    private static final Logger LOG = Logger.getLogger(RedisAgent.class.getName());
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

    private final AgentRuntime runtime;
    private final Sync sync;
    private final @Nullable AclLog log;

    private RedisAgent(AgentRuntime runtime, Sync sync, @Nullable AclLog log)
    {
        this.runtime = runtime;
        this.sync = sync;
        this.log = log;
    }

    /**
     * Starts the agent.
     *
     * @param config its settings
     * @return the running agent; {@link #close} it to stop
     * @throws RedisFailure when no node can be reached or Redis refuses the agent's user
     * @throws IllegalStateException when the deployment is older than the agent supports
     */
    public static RedisAgent start(AgentConfig config)
    {
        return start(config, JedisRedis::new);
    }

    // The connections are handed to the runtime, which closes them when the agent stops.
    @SuppressWarnings("PMD.CloseResource")
    static RedisAgent start(AgentConfig config, Function<AgentConfig.Connection, Redis> connect)
    {
        Redis syncing = connect.apply(config.connection());
        Redis reading = null;
        try {
            Topology topology = syncing.topology();
            String compatible = String.valueOf(Topology.fields(String.valueOf(syncing.command(topology.addresses().get(0), "INFO", "server"))).get("redis_version"));
            String unsupported = unsupported(compatible);
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            String self = String.valueOf(syncing.command(topology.addresses().get(0), "ACL", "WHOAMI"));
            if (config.aclLog() != null) {
                reading = connect.apply(config.connection());
            }
            AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "Redis", LOG, RedisFailure.class);
            runtime.closes(syncing::close);
            Sync sync = new Sync(syncing, config, self, runtime.agent()::snapshot, Clock.systemUTC());
            runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
            AclLog log = null;
            Duration interval = config.aclLog();
            Redis logs = reading;
            if (logs != null && interval != null) {
                runtime.closes(logs::close);
                log = new AclLog(logs, self, config.agent().cacheDirectory().resolve("redis-acl-log.position"), runtime.agent()::snapshot, runtime.audit()::record);
                runtime.every("read the ACL logs", interval, interval, log::poll);
            }
            LOG.info("The GrantForge Redis agent keeps " + topology.product() + " " + topology.version() + " on " + topology.addresses().size() + " nodes in step with its"
                    + " policies every " + config.syncInterval().toMillis() + " ms" + (log == null ? "" : " and reads their ACL logs"));
            return new RedisAgent(runtime, sync, log);
        }
        catch (RuntimeException failed) {
            if (reading != null) {
                reading.close();
            }
            syncing.close();
            throw failed;
        }
    }

    /**
     * Says why a deployment cannot be kept by the agent.
     *
     * @param version the Redis release, such as {@code 7.2.17}, which Valkey shows too
     * @return the reason, or {@code null} for a supported release
     */
    static @Nullable String unsupported(String version)
    {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) {
            return "the Redis version cannot be read: " + version;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (major < MINIMUM[0] || major == MINIMUM[0] && minor < MINIMUM[1]) {
            return "the deployment is Redis " + version + "; the GrantForge agent needs Redis " + MINIMUM[0] + "." + MINIMUM[1] + " or Valkey, or later";
        }
        return null;
    }

    /**
     * Brings Redis in step now, rather than at the next turn.
     *
     * @return what changed, or {@code null} without a snapshot
     */
    Reconciler.@Nullable Outcome syncNow()
    {
        return sync.run();
    }

    /**
     * Reads the ACL logs now, rather than at the next turn.
     *
     * @return how many entries were read
     */
    int readLogNow()
    {
        return log == null ? 0 : log.poll();
    }

    /**
     * Returns the signed-policy agent.
     *
     * @return it
     */
    GrantForgeAgent agent()
    {
        return runtime.agent();
    }

    /** Stops the timers, sends or spools the events and lets go of the nodes; Redis keeps what it was given. */
    @Override
    public void close()
    {
        runtime.close();
    }
}
