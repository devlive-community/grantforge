// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.policy.engine.AccessRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Warns, once each, of what the agent cannot take away: a command is allowed when a user's root permissions or any of
 * its selectors allow it, so root permissions the operator gives decide beside the agent's selectors. Rather than read
 * the rules, the agent asks Redis itself with {@code ACL DRYRUN} whether a user may run a command on a key or channel a
 * policy denies it, a key or channel standing for each pattern the policy names. Also warned of are users no selector
 * binds and a default user anyone may sign in as.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    private final Redis redis;
    private final Notices notices = new Notices(LOG);

    NativeConflicts(Redis redis)
    {
        this.redis = redis;
    }

    /**
     * Checks the deployment.
     *
     * @param state what the deployment holds
     * @param users the users the agent looks after
     * @param snapshot the policies
     * @param now the moment whose policies apply
     * @throws RedisFailure when a node cannot be reached
     */
    void check(ServerState state, Set<String> users, Snapshot snapshot, Instant now)
    {
        List<String> nodes = state.topology().addresses();
        for (String user : state.names()) {
            if (state.superuser(user)) {
                notices.once(Level.WARNING, "Redis user " + user + " may run every command on every key, which no selector binds; GrantForge keeps no selector for it");
            }
            for (String node : nodes) {
                UserAcl acl = state.acl(node, user);
                if (acl != null && acl.open()) {
                    notices.once(Level.WARNING, "Redis user " + user + " needs no password on " + node + ", so anyone may sign in as it");
                }
            }
        }
        if (nodes.isEmpty()) {
            return;
        }
        Patterns keys = new Patterns(snapshot, Planner.KEY);
        Patterns channels = new Patterns(snapshot, Planner.CHANNEL);
        for (String user : users) {
            for (String pattern : keys.values()) {
                probe(user, Planner.READ, Planner.KEY, pattern, nodes.get(0), snapshot, now, "GET", example(pattern));
                probe(user, Planner.WRITE, Planner.KEY, pattern, nodes.get(0), snapshot, now, "SET", example(pattern), "x");
            }
            for (String pattern : channels.values()) {
                probe(user, Planner.SUBSCRIBE, Planner.CHANNEL, pattern, nodes.get(0), snapshot, now, "SUBSCRIBE", example(pattern));
                probe(user, Planner.PUBLISH, Planner.CHANNEL, pattern, nodes.get(0), snapshot, now, "PUBLISH", example(pattern), "x");
            }
            for (String node : nodes) {
                probe(user, Planner.MONITOR, Planner.NODE, node, node, snapshot, now, "INFO");
            }
        }
    }

    /** A key or channel a pattern matches: its wildcards made letters. */
    static String example(String pattern)
    {
        return pattern.replace('*', 'x').replace('?', 'x');
    }

    private void probe(String user, String type, String level, String value, String node, Snapshot snapshot, Instant now, String... command)
    {
        AgentDecision decision = snapshot.decide(AccessRequest.builder(user, type).resource(level, value).time(now).build());
        if (decision.outcome() != AgentDecision.Outcome.DENIED) {
            return;
        }
        String[] dryRun = new String[command.length + 3];
        dryRun[0] = "ACL";
        dryRun[1] = "DRYRUN";
        dryRun[2] = user;
        System.arraycopy(command, 0, dryRun, 3, command.length);
        Object answer;
        try {
            answer = redis.command(node, dryRun);
        }
        catch (RedisFailure refused) {
            if (refused.unreachable()) {
                throw refused;
            }
            return;
        }
        if ("OK".equals(String.valueOf(answer))) {
            notices.once(Level.WARNING, "Redis user " + user + " may " + type + " " + (Planner.NODE.equals(level) ? "node " : level + " ") + value
                    + " through permissions outside GrantForge, which GrantForge policy " + decision.policyId() + " denies");
        }
    }
}
