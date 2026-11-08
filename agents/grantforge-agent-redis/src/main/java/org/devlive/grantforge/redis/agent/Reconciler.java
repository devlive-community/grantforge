// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Brings the users' selectors in step with the plans, node by node, as Redis keeps an ACL on each. The agent keeps every
 * selector of the users it looks after and none of their root permissions; one {@code ACL SETUSER user clearselectors
 * (...)} replaces them all, so a user never holds both the old and the new, or neither, and what was changed by hand is
 * set right the same way. A node that keeps its ACL in a file saves it after a change; a node that does not loses the
 * selectors when it restarts, and gets them again at the next sync.
 */
final class Reconciler
{
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    private final Redis redis;
    /** What Redis showed after the agent set a user's selectors, by node and user, for rules Redis writes its own way. */
    private final Map<String, Shown> shown = new HashMap<>();

    /** What the agent asked for, and what Redis showed afterwards. */
    private record Shown(List<Selector> asked, List<Selector> shown)
    {
    }

    /**
     * What one sync changed.
     *
     * @param users the users planned
     * @param replaced the users' selectors replaced, counted by node
     * @param saved the nodes whose ACL file was saved
     * @param missing the users some node did not have, counted by node
     * @param failed the changes that failed
     */
    record Outcome(int users, int replaced, int saved, int missing, int failed)
    {
        boolean changed()
        {
            return replaced + saved + failed > 0;
        }
    }

    Reconciler(Redis redis)
    {
        this.redis = redis;
    }

    /**
     * Brings the selectors in step.
     *
     * @param state what the deployment holds
     * @param users the users the agent looks after
     * @param plans what each user is planned to hold, or {@code null} for nothing at all
     * @return what changed
     * @throws RedisFailure when a node cannot be reached
     */
    // A command and what Redis showed per user changed.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Outcome reconcile(ServerState state, Set<String> users, Function<String, Plan> plans)
    {
        int replaced = 0;
        int saved = 0;
        int missing = 0;
        int failed = 0;
        Map<String, Plan> planned = new HashMap<>();
        users.forEach(user -> planned.put(user, plans.apply(user)));
        for (String node : state.topology().addresses()) {
            boolean dirty = false;
            for (String user : users) {
                UserAcl acl = state.acl(node, user);
                if (acl == null) {
                    missing++;
                    continue;
                }
                List<Selector> wanted = planned.getOrDefault(user, Plan.NOTHING).on(node);
                if (same(node, user, acl.selectors(), wanted)) {
                    continue;
                }
                try {
                    List<String> command = new ArrayList<>(List.of("ACL", "SETUSER", user, "clearselectors"));
                    wanted.forEach(selector -> command.add(selector.rule()));
                    redis.command(node, command.toArray(new String[0]));
                    shown.put(node + "\u0000" + user, new Shown(wanted, UserAcl.of(redis.command(node, "ACL", "GETUSER", user)).selectors()));
                    replaced++;
                    dirty = true;
                }
                catch (RedisFailure refused) {
                    if (refused.unreachable()) {
                        throw refused;
                    }
                    LOG.log(Level.WARNING, "The GrantForge agent could not bring user " + user + " in step on " + node + ": " + refused.getMessage());
                    failed++;
                }
            }
            if (dirty && state.aclFiles().contains(node)) {
                try {
                    redis.command(node, "ACL", "SAVE");
                    saved++;
                }
                catch (RedisFailure refused) {
                    if (refused.unreachable()) {
                        throw refused;
                    }
                    LOG.log(Level.WARNING, "The GrantForge agent could not save the ACL file of " + node + ": " + refused.getMessage());
                    failed++;
                }
            }
        }
        return new Outcome(users.size(), replaced, saved, missing, failed);
    }

    /** Whether a user's selectors are what is wanted, as the agent writes them or as Redis showed them after it did. */
    private boolean same(String node, String user, List<Selector> held, List<Selector> wanted)
    {
        if (Set.copyOf(held).equals(Set.copyOf(wanted)) && held.size() == wanted.size()) {
            return true;
        }
        Shown last = shown.get(node + "\u0000" + user);
        return last != null && last.asked().equals(wanted) && Set.copyOf(last.shown()).equals(Set.copyOf(held)) && last.shown().size() == held.size();
    }
}
