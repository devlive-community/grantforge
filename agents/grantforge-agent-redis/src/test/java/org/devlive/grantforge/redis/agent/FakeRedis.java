// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A deployment that answers from a table, by the start of a command, and remembers the commands that are not reads. */
final class FakeRedis
        implements Redis
{
    /** The commands that are not answered from the table, as {@code node command words}. */
    final List<String> changes = new ArrayList<>();
    boolean closed;

    private Topology topology;
    private final Map<String, Object> answers = new LinkedHashMap<>();
    private final Map<String, RedisFailure> failures = new LinkedHashMap<>();

    FakeRedis(Topology topology)
    {
        this.topology = topology;
    }

    /** A primary with one replica. */
    static FakeRedis replicated()
    {
        return new FakeRedis(new Topology("Redis", "7.2.17", false, List.of(new Topology.Node("p:6379", true), new Topology.Node("r:6379", false))));
    }

    /** Answers commands that start with some words, on any node or, before that, on the one named first as {@code node|}. */
    FakeRedis answer(String start, @Nullable Object answer)
    {
        answers.put(start, answer);
        return this;
    }

    /** Fails commands that start with some words. */
    FakeRedis fail(String start, boolean unreachable)
    {
        failures.put(start, new RedisFailure(start + " failed", unreachable, null));
        return this;
    }

    FakeRedis topology(Topology changed)
    {
        topology = changed;
        return this;
    }

    @Override
    public Topology topology()
    {
        RedisFailure failure = failures.get("TOPOLOGY");
        if (failure != null) {
            throw failure;
        }
        return topology;
    }

    @Override
    public @Nullable Object command(String node, String... command)
    {
        String text = String.join(" ", command);
        for (Map.Entry<String, RedisFailure> failure : failures.entrySet()) {
            if ((node + "|" + text).startsWith(failure.getKey()) || text.startsWith(failure.getKey())) {
                throw failure.getValue();
            }
        }
        for (Map.Entry<String, Object> answer : answers.entrySet()) {
            if ((node + "|" + text).startsWith(answer.getKey())) {
                return answer.getValue();
            }
        }
        for (Map.Entry<String, Object> answer : answers.entrySet()) {
            if (text.startsWith(answer.getKey())) {
                return answer.getValue();
            }
        }
        changes.add(node + " " + text);
        return "OK";
    }

    @Override
    public void close()
    {
        closed = true;
    }
}
