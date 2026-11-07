// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A deployment that answers from tables and remembers the commands that change something. */
final class FakeMongo
        implements MongoDb
{
    /** The commands run, as {@code database command-name first-value}, without those that only read. */
    final List<String> changes = new ArrayList<>();
    /** The whole documents of those commands. */
    final List<Document> changed = new ArrayList<>();
    boolean closed;

    private final Map<String, Document> answers = new HashMap<>();
    private final Map<String, MongoFailure> failures = new HashMap<>();
    private final Map<String, List<String>> collections = new HashMap<>();
    private final Map<String, List<Document>> profiles = new HashMap<>();
    private @Nullable MongoFailure profileFailure;

    /** Answers a command by its first key. */
    FakeMongo answer(String command, Document answer)
    {
        answers.put(command, answer);
        return this;
    }

    /** Fails a command by its first key, or by {@code command value} for one value. */
    FakeMongo fail(String command, boolean unreachable)
    {
        failures.put(command, new MongoFailure(command + " failed", unreachable, null));
        return this;
    }

    /** Has a database with collections. */
    FakeMongo database(String name, String... held)
    {
        collections.put(name, List.of(held));
        return this;
    }

    /** Has profiler entries of a database. */
    FakeMongo profile(String database, Document... entries)
    {
        profiles.put(database, List.of(entries));
        return this;
    }

    /** Fails reading the profiler. */
    FakeMongo failProfile(boolean unreachable)
    {
        profileFailure = new MongoFailure("not authorized", unreachable, null);
        return this;
    }

    @Override
    public Document command(String database, Document command)
    {
        String key = command.keySet().iterator().next();
        String named = key + " " + command.get(key);
        MongoFailure failure = failures.getOrDefault(named, failures.get(key));
        if (failure != null) {
            throw failure;
        }
        if (!answers.containsKey(key)) {
            changes.add(database + " " + named);
            changed.add(command);
        }
        return answers.getOrDefault(key, new Document());
    }

    @Override
    public List<String> databases()
    {
        return List.copyOf(collections.keySet());
    }

    @Override
    public List<String> collections(String database)
    {
        return collections.getOrDefault(database, List.of());
    }

    @Override
    public List<Document> profile(String database, Instant after, Instant until, int limit)
    {
        if (profileFailure != null) {
            throw profileFailure;
        }
        return profiles.getOrDefault(database, List.of()).stream().limit(limit).toList();
    }

    @Override
    public void close()
    {
        closed = true;
    }
}
