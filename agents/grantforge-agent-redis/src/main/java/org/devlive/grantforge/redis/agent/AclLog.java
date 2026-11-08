// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.PositionFile;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports the commands Redis refused from each node's ACL log: who was refused which key, channel or command, from where
 * and when, with whether GrantForge's policies decide as Redis did. Redis keeps no record of what it allowed, so only
 * refusals are reported. Redis folds a refusal repeated within a minute into the entry before it, so the agent reads
 * each node's entries updated since the last read, and reports a folded entry again each time it is updated. Refused
 * sign-ins are left out, as they name no access, and so are refusals of commands the user may not run at all, which
 * Redis logs without their keys.
 */
final class AclLog
{
    /** The most entries one read asks a node for, as many as Redis keeps by default. */
    static final int ENTRIES = 128;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(AclLog.class.getName());

    private final Redis redis;
    private final String self;
    private final PositionFile position;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Consumer<AccessEvent> sink;
    private final Notices notices = new Notices(LOG);
    private final Map<String, Boolean> writes = new ConcurrentHashMap<>();

    AclLog(Redis redis, String self, Path position, Supplier<@Nullable Snapshot> snapshots, Consumer<AccessEvent> sink)
    {
        this.redis = redis;
        this.self = self;
        this.position = new PositionFile(position);
        this.snapshots = snapshots;
        this.sink = sink;
    }

    /**
     * One refusal.
     *
     * @param id the entry's id on its node
     * @param reason what was refused: {@code key}, {@code channel}, {@code command} or {@code auth}
     * @param object the key, channel or command
     * @param user the user
     * @param command the command the client ran
     * @param address the client's address, or {@code null}
     * @param at when it was last refused
     */
    record Entry(long id, String reason, String object, String user, String command, @Nullable String address, Instant at)
    {
    }

    /**
     * Reads the entries written since the last read.
     *
     * @return how many entries were read
     * @throws RedisFailure when the deployment cannot be reached
     */
    int poll()
    {
        Map<String, Long> cursors = load();
        Snapshot snapshot = snapshots.get();
        int read = 0;
        for (String node : redis.topology().addresses()) {
            List<Entry> entries;
            try {
                entries = entries(redis.command(node, "ACL", "LOG", Integer.toString(ENTRIES)));
            }
            catch (RedisFailure refused) {
                if (refused.unreachable()) {
                    throw refused;
                }
                notices.once(Level.WARNING, "The GrantForge agent cannot read the ACL log of " + node + ": " + refused.getMessage());
                continue;
            }
            Long last = cursors.get(node);
            long newest = entries.stream().mapToLong(entry -> entry.at().toEpochMilli()).max().orElse(last == null ? 0 : last);
            if (last != null) {
                for (Entry entry : entries) {
                    if (entry.at().toEpochMilli() > last) {
                        report(node, entry, snapshot);
                        read++;
                    }
                }
            }
            cursors.put(node, Math.max(newest, last == null ? 0 : last));
        }
        save(cursors);
        return read;
    }

    /**
     * Reads the entries of an {@code ACL LOG} answer, oldest first, each at the time it was last updated.
     *
     * @param answer the answer
     * @return the entries
     */
    // A map and an entry per entry of the log.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static List<Entry> entries(@Nullable Object answer)
    {
        List<Entry> entries = new ArrayList<>();
        if (answer instanceof List<?> list) {
            for (Object each : list) {
                Map<String, String> fields = new LinkedHashMap<>();
                if (each instanceof List<?> pairs) {
                    for (int index = 0; index + 1 < pairs.size(); index += 2) {
                        fields.put(String.valueOf(pairs.get(index)), String.valueOf(pairs.get(index + 1)));
                    }
                }
                Map<String, String> client = client(fields.getOrDefault("client-info", ""));
                String address = client.get("addr");
                if (address != null && address.lastIndexOf(':') > 0) {
                    address = address.substring(0, address.lastIndexOf(':'));
                }
                entries.add(new Entry(number(fields.get("entry-id")), fields.getOrDefault("reason", ""), fields.getOrDefault("object", ""),
                        fields.getOrDefault("username", ""), client.getOrDefault("cmd", fields.getOrDefault("object", "")),
                        address, Instant.ofEpochMilli(number(fields.getOrDefault("timestamp-last-updated", fields.get("timestamp-created"))))));
            }
        }
        entries.sort(Comparator.comparing(Entry::at).thenComparingLong(Entry::id));
        return entries;
    }

    private static long number(@Nullable String text)
    {
        try {
            return text == null ? 0 : Long.parseLong(text.strip());
        }
        catch (NumberFormatException none) {
            return 0;
        }
    }

    private static Map<String, String> client(String info)
    {
        Map<String, String> fields = new HashMap<>();
        for (String pair : info.split(" ")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                fields.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        return fields;
    }

    private void report(String node, Entry entry, @Nullable Snapshot snapshot)
    {
        if (entry.user().isEmpty() || entry.user().equals(self)) {
            return;
        }
        String level;
        String type;
        String resource;
        switch (entry.reason()) {
            case "key" -> {
                level = Planner.KEY;
                type = write(node, entry.command()) ? Planner.WRITE : Planner.READ;
                resource = entry.object();
            }
            case "channel" -> {
                level = Planner.CHANNEL;
                type = entry.command().endsWith("publish") ? Planner.PUBLISH : Planner.SUBSCRIBE;
                resource = entry.object();
            }
            case "command" -> {
                if (!Selector.MONITOR.contains("+" + entry.object())) {
                    return;
                }
                level = Planner.NODE;
                type = Planner.MONITOR;
                resource = node;
            }
            default -> {
                return;
            }
        }
        AccessEvent.Builder built = AccessEvent.builder(entry.user(), resource, type, false).occurredAt(entry.at()).clientIp(entry.address()).resourceType(level)
                .action(entry.command()).request(entry.command() + " on " + node);
        if (snapshot != null) {
            AgentDecision decision = snapshot.decide(AccessRequest.builder(entry.user(), type).resource(level, resource).time(entry.at()).build());
            if (!decision.allowed()) {
                built.decidedBy(decision).enforcedByGrantForge();
            }
        }
        sink.accept(built.build());
    }

    /** Whether a command writes, as Redis's {@code COMMAND INFO} flags it; asked once per command. */
    private boolean write(String node, String command)
    {
        return writes.computeIfAbsent(command, name -> {
            try {
                return flags(redis.command(node, "COMMAND", "INFO", name)).contains("write");
            }
            catch (RedisFailure unknown) {
                return false;
            }
        });
    }

    /**
     * Reads the flags of a {@code COMMAND INFO} answer of one command.
     *
     * @param answer the answer
     * @return the flags, none for an answer without them
     */
    static Set<String> flags(@Nullable Object answer)
    {
        if (answer instanceof List<?> commands && !commands.isEmpty() && commands.get(0) instanceof List<?> command && command.size() > 2
                && command.get(2) instanceof List<?> flags) {
            Set<String> names = new TreeSet<>();
            flags.forEach(flag -> names.add(String.valueOf(flag)));
            return names;
        }
        return Set.of();
    }

    private Map<String, Long> load()
    {
        Map<String, Long> cursors = new HashMap<>();
        try {
            String text = position.read();
            if (text != null) {
                JSON.readTree(text).properties().forEach(entry -> cursors.put(entry.getKey(), entry.getValue().asLong()));
            }
        }
        catch (IOException unreadable) {
            notices.once(Level.WARNING, "The GrantForge agent cannot read where it stopped reading the ACL logs, " + position.path()
                    + ", and reads on from the present: " + unreadable.getMessage());
        }
        return cursors;
    }

    private void save(Map<String, Long> cursors)
    {
        ObjectNode saved = JSON.createObjectNode();
        cursors.forEach(saved::put);
        try {
            position.write(saved.toString());
        }
        catch (IOException unwritable) {
            LOG.log(Level.WARNING, "The GrantForge agent cannot keep where it stopped reading the ACL logs, " + position.path(), unwritable);
        }
    }
}
