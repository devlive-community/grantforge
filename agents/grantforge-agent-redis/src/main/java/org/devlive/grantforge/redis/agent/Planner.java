// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Turns the policies of a snapshot into what each user's selectors hold. Redis grants keys and channels by pattern and
 * cannot be told which keys there are, so each pattern of a policy is granted as itself, where the policies decide the
 * user the same across it; one they decide differently within is withheld and said so, as granting it would give
 * what a policy denies. One selector holds the keys, read, written or both, with the commands that read and write
 * data, never those Redis counts as dangerous, and never those that list keys unless the user may read every key; one
 * holds the channels the user may subscribe to and one those it may publish to; and the nodes the user may monitor hold
 * one selector of the commands that show a node's state.
 */
final class Planner
{
    static final String KEY = "key";
    static final String CHANNEL = "channel";
    static final String NODE = "node";

    static final String READ = "read";
    static final String WRITE = "write";
    static final String SUBSCRIBE = "subscribe";
    static final String PUBLISH = "publish";
    static final String MONITOR = "monitor";

    /** The commands that list keys whatever their patterns, which only a user that may read every key gets. */
    static final List<String> LISTING = List.of("-scan", "-randomkey");

    private final Snapshot snapshot;
    private final Patterns keys;
    private final Patterns channels;
    private final List<String> nodes;
    private final Instant now;
    private final Consumer<String> withheld;

    /**
     * Plans with a snapshot.
     *
     * @param snapshot the policies
     * @param nodes the nodes of the deployment
     * @param now the moment the policies are taken at
     * @param withheld told of each pattern a policy allows that Redis cannot be given without giving more
     */
    Planner(Snapshot snapshot, List<String> nodes, Instant now, Consumer<String> withheld)
    {
        this.snapshot = snapshot;
        this.keys = new Patterns(snapshot, KEY);
        this.channels = new Patterns(snapshot, CHANNEL);
        this.nodes = List.copyOf(nodes);
        this.now = now;
        this.withheld = withheld;
    }

    /**
     * Plans what a user's selectors hold.
     *
     * @param user the user
     * @return the plan
     */
    Plan plan(String user)
    {
        List<Selector> selectors = new ArrayList<>();
        SortedSet<String> read = granted(user, READ, KEY, keys, "keys");
        SortedSet<String> written = granted(user, WRITE, KEY, keys, "keys");
        if (!read.isEmpty() || !written.isEmpty()) {
            SortedSet<String> rules = new TreeSet<>();
            read.forEach(pattern -> rules.add((written.contains(pattern) ? "~" : "%R~") + pattern));
            written.stream().filter(pattern -> !read.contains(pattern)).forEach(pattern -> rules.add("%W~" + pattern));
            List<String> commands = new ArrayList<>();
            if (!read.isEmpty()) {
                commands.add("+@read");
            }
            if (!written.isEmpty()) {
                commands.add("+@write");
            }
            commands.add("-@dangerous");
            if (!read.contains("*")) {
                commands.addAll(LISTING);
            }
            selectors.add(Selector.of(commands, rules, new TreeSet<>()));
        }
        SortedSet<String> subscribed = granted(user, SUBSCRIBE, CHANNEL, channels, "channels");
        if (!subscribed.isEmpty()) {
            selectors.add(Selector.of(List.of("+subscribe", "+psubscribe", "+ssubscribe", "+unsubscribe", "+punsubscribe", "+sunsubscribe"), new TreeSet<>(),
                    channelRules(subscribed)));
        }
        SortedSet<String> published = granted(user, PUBLISH, CHANNEL, channels, "channels");
        if (!published.isEmpty()) {
            selectors.add(Selector.of(List.of("+publish", "+spublish"), new TreeSet<>(), channelRules(published)));
        }
        Set<String> monitored = new TreeSet<>();
        for (String node : nodes) {
            if (decide(user, MONITOR, NODE, node)) {
                monitored.add(node);
            }
        }
        return new Plan(selectors, monitored);
    }

    private static SortedSet<String> channelRules(SortedSet<String> patterns)
    {
        SortedSet<String> rules = new TreeSet<>();
        patterns.forEach(pattern -> rules.add("&" + pattern));
        return rules;
    }

    /** The patterns of a level an access type is allowed on for a user; those it cannot be granted on are said so. */
    private SortedSet<String> granted(String user, String type, String level, Patterns patterns, String what)
    {
        SortedSet<String> granted = new TreeSet<>();
        for (String value : patterns.values()) {
            if (!decide(user, type, level, value)) {
                continue;
            }
            if (Patterns.grantable(value) && patterns.uniform(value, name -> decide(user, type, level, name))) {
                granted.add(value);
            }
            else {
                withheld.accept(type + " of " + what + " " + value + " for user " + user + ", as " + (Patterns.grantable(value) ? "policies decide " + what
                        + " within it differently" : "Redis cannot take it as a pattern of its own"));
            }
        }
        return granted;
    }

    private boolean decide(String user, String type, String level, String value)
    {
        return snapshot.decide(AccessRequest.builder(user, type).resource(level, value).time(now).build()).allowed();
    }
}
