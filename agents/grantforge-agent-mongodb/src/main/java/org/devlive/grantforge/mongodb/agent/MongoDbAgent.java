// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.sync.AgentRuntime;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A running agent: the signed-policy agent that keeps the MongoDB service's snapshot and ships access events, the sync that
 * keeps MongoDB's roles in step with it, and the reader of accesses. Each runs on a timer; a sync or read that fails is
 * tried again at the next turn.
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
public final class MongoDbAgent
        implements AutoCloseable
{
    /** The oldest MongoDB release the agent supports: the oldest it is tested with. */
    static final int[] MINIMUM = {6, 0};

    private static final Logger LOG = Logger.getLogger(MongoDbAgent.class.getName());
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

    private final AgentRuntime runtime;
    private final Sync sync;
    private final IntSupplier reader;

    private MongoDbAgent(AgentRuntime runtime, Sync sync, IntSupplier reader)
    {
        this.runtime = runtime;
        this.sync = sync;
        this.reader = reader;
    }

    /**
     * Starts the agent.
     *
     * @param config its settings
     * @return the running agent; {@link #close} it to stop
     * @throws MongoFailure when no server can be reached or MongoDB refuses the agent's user
     * @throws IllegalStateException when the deployment is older than the agent supports
     */
    public static MongoDbAgent start(AgentConfig config)
    {
        return start(config, DriverMongoDb::new);
    }

    static MongoDbAgent start(AgentConfig config, Function<AgentConfig.Connection, MongoDb> connect)
    {
        MongoDb mongo = connect.apply(config.connection());
        try {
            Document info = mongo.command(ServerState.ROLE_DATABASE, new Document("buildInfo", 1));
            String version = String.valueOf(info.getString("version"));
            String unsupported = unsupported(version);
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            return start(config, version, mongo, self(mongo, config.connection()));
        }
        catch (RuntimeException failed) {
            mongo.close();
            throw failed;
        }
    }

    private static MongoDbAgent start(AgentConfig config, String version, MongoDb mongo, String self)
    {
        AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "MongoDB", LOG, MongoFailure.class);
        runtime.closes(mongo::close);
        Sync sync = new Sync(mongo, config, self, runtime.agent()::snapshot, Clock.systemUTC());
        runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
        AgentConfig.Audit audit = config.audit();
        IntSupplier reader = () -> 0;
        String accesses = "";
        if (audit != null) {
            AccessReporter reporter = new AccessReporter(self, runtime.agent()::snapshot, sync::groupsOf, sync::inventory, runtime.audit()::record);
            if (audit.files().isEmpty()) {
                Profiler profiler = new Profiler(mongo, audit, config.agent().cacheDirectory().resolve("mongodb-profiler.position"), sync::inventory, reporter, Clock.systemUTC());
                reader = profiler::poll;
                runtime.every("read accesses", audit.interval(), audit.interval(), profiler::poll);
                accesses = " and reads accesses from its profiler";
            }
            else {
                AuditFiles files = new AuditFiles(audit, config.agent().cacheDirectory().resolve("mongodb-audit-log.position"), reporter);
                reader = files::poll;
                runtime.every("read accesses", audit.interval(), audit.interval(), files::poll);
                accesses = " and reads accesses from its audit log";
            }
        }
        LOG.info("The GrantForge MongoDB agent keeps MongoDB " + version + " in step with its policies every " + config.syncInterval().toMillis() + " ms" + accesses);
        return new MongoDbAgent(runtime, sync, reader);
    }

    /**
     * Says who the agent signed in as, which for a client certificate only the server knows.
     *
     * @param mongo the deployment
     * @param connection the settings
     * @return the user's name
     */
    private static String self(MongoDb mongo, AgentConfig.Connection connection)
    {
        Document info = mongo.command(ServerState.ROLE_DATABASE, new Document("connectionStatus", 1)).get("authInfo", Document.class);
        List<Document> users = info == null ? null : info.getList("authenticatedUsers", Document.class);
        if (users != null && !users.isEmpty()) {
            return String.valueOf(users.get(0).getString("user"));
        }
        return String.valueOf(connection.user());
    }

    /**
     * Says why a deployment cannot be kept by the agent.
     *
     * @param version the release, such as {@code 8.0.34}
     * @return the reason, or {@code null} for a supported release
     */
    static @Nullable String unsupported(String version)
    {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) {
            return "the MongoDB version cannot be read: " + version;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (major < MINIMUM[0] || major == MINIMUM[0] && minor < MINIMUM[1]) {
            return "the deployment is MongoDB " + version + "; the GrantForge agent needs MongoDB " + MINIMUM[0] + "." + MINIMUM[1] + " or later";
        }
        return null;
    }

    /**
     * Brings MongoDB in step now, rather than at the next turn.
     *
     * @return what changed, or {@code null} without a snapshot
     */
    Reconciler.@Nullable Outcome syncNow()
    {
        return sync.run();
    }

    /**
     * Reads accesses now, rather than at the next turn.
     *
     * @return how many entries were read
     */
    int readAccessesNow()
    {
        return reader.getAsInt();
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

    /** Stops the timers, sends or spools the events and lets go of MongoDB; MongoDB keeps what it was given. */
    @Override
    public void close()
    {
        runtime.close();
    }
}
