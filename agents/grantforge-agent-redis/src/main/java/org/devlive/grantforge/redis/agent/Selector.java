// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * One ACL selector of a user, as Redis shows it: the commands it allows, the key patterns it covers, each for reading
 * ({@code %R~}), writing ({@code %W~}) or both ({@code ~}), and the channel patterns ({@code &}). A command is allowed
 * when the user's root permissions or one of its selectors allows it with all its keys and channels.
 *
 * @param commands the command rules as Redis shows them, such as {@code -@all +@read}
 * @param keys the key rules, sorted
 * @param channels the channel rules, sorted
 */
record Selector(String commands, SortedSet<String> keys, SortedSet<String> channels)
{
    /** The commands of monitoring a node: its state and sizes, not what clients sent. */
    static final List<String> MONITOR = List.of("+info", "+dbsize", "+latency|latest", "+latency|history", "+memory|stats");

    /** Copies the rules. */
    Selector
    {
        keys = new TreeSet<>(keys);
        channels = new TreeSet<>(channels);
    }

    /**
     * Builds a selector of command rules that Redis shows after {@code -@all}.
     *
     * @param rules the command rules, in their order
     * @param keys the key rules
     * @param channels the channel rules
     * @return the selector
     */
    static Selector of(List<String> rules, SortedSet<String> keys, SortedSet<String> channels)
    {
        return new Selector(("-@all " + String.join(" ", rules)).strip(), keys, channels);
    }

    /**
     * Writes the selector as {@code ACL SETUSER} takes it.
     *
     * @return such as {@code (%R~sales:* -@all +@read)}
     */
    String rule()
    {
        List<String> rules = new ArrayList<>(keys);
        rules.addAll(channels);
        rules.add(commands);
        return "(" + String.join(" ", rules) + ")";
    }

    /**
     * Reads a selector from what {@code ACL GETUSER} shows of it.
     *
     * @param commands its commands
     * @param keys its key rules, separated by spaces
     * @param channels its channel rules, separated by spaces
     * @return the selector
     */
    static Selector shown(String commands, String keys, String channels)
    {
        return new Selector(commands.strip(), words(keys), words(channels));
    }

    private static SortedSet<String> words(String text)
    {
        SortedSet<String> words = new TreeSet<>();
        for (String word : text.strip().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }
}
