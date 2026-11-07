// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilerTest
{
    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");

    @TempDir
    Path directory;

    private final List<AccessEvent> events = new ArrayList<>();
    private final FakeMongo mongo = new FakeMongo().database("sales", "orders").database("admin");
    private @Nullable Inventory inventory = new Inventory("rs0", List.of("sales", "admin"), database -> List.of());

    private Profiler profiler(int batch)
    {
        AccessReporter reporter = new AccessReporter("grantforge", () -> null, user -> Set.of(), () -> inventory, events::add);
        return new Profiler(mongo, new AgentConfig.Audit(List.of(), Duration.ofSeconds(2), Duration.ofSeconds(5), batch), directory.resolve("position"), () -> inventory, reporter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Document entry(String operation, String user, Document command)
    {
        return new Document("op", operation).append("ns", "sales.orders").append("user", user).append("client", "10.0.0.9").append("ts", Date.from(NOW.minusSeconds(5)))
                .append("command", command);
    }

    @Test
    void readsWhatAnEntryTellsOf()
    {
        AccessReporter.Command query = requireNonNull(Profiler.command(entry("query", "alice@admin", new Document("find", "orders"))), "command");
        assertThat(query.user()).isEqualTo("alice");
        assertThat(query.command()).isEqualTo("find");
        assertThat(query.allowed()).isTrue();
        assertThat(query.address()).isEqualTo("10.0.0.9");
        assertThat(query.at()).isEqualTo(NOW.minusSeconds(5));
        assertThat(query.accesses()).containsExactly(new Accesses.Access(Actions.READ, "sales", "orders"));
        assertThat(requireNonNull(Profiler.command(entry("insert", "alice", new Document("insert", "orders"))), "command").user()).isEqualTo("alice");
        assertThat(requireNonNull(Profiler.command(entry("update", "alice", new Document("q", 1))), "command").accesses()).containsExactly(new Accesses.Access(Actions.UPDATE, "sales", "orders"));
        assertThat(requireNonNull(Profiler.command(entry("remove", "alice", new Document("q", 1))), "command").accesses()).containsExactly(new Accesses.Access(Actions.REMOVE, "sales", "orders"));
        assertThat(requireNonNull(Profiler.command(entry("command", "alice", new Document("drop", "orders"))), "command").accesses()).containsExactly(new Accesses.Access(Actions.DROP, "sales", "orders"));
        assertThat(requireNonNull(Profiler.command(entry("command", "alice", new Document())), "command").accesses()).isEmpty();
        assertThat(requireNonNull(Profiler.command(entry("getmore", "alice", new Document("getMore", 1))), "command").accesses()).isEmpty();
    }

    @Test
    void leavesOutEntriesWithoutWhatItNeeds()
    {
        assertThat(Profiler.command(new Document())).isNull();
        assertThat(Profiler.command(new Document("op", "query"))).isNull();
        assertThat(Profiler.command(new Document("op", "query").append("ns", "sales.orders"))).isNull();
        assertThat(Profiler.command(new Document("op", "query").append("ns", "sales.orders").append("user", ""))).isNull();
        AccessReporter.Command bare = requireNonNull(Profiler.command(new Document("op", "query").append("ns", "sales.orders").append("user", "a")), "command");
        assertThat(bare.at()).isNotNull();
    }

    @Test
    void startsFromThePresentThenReadsOn() throws IOException
    {
        mongo.profile("sales", entry("query", "alice@admin", new Document("find", "orders")));
        Profiler profiler = profiler(10);
        assertThat(profiler.poll()).as("a database read for the first time starts here").isZero();
        assertThat(Files.readString(directory.resolve("position"))).contains("sales").doesNotContain("admin");
        assertThat(profiler.poll()).isEqualTo(1);
        assertThat(events).singleElement().satisfies(event -> assertThat(EventFields.of(event)).containsEntry("user", "alice").containsEntry("resource", "sales.orders"));
    }

    @Test
    void readsNothingBeforeTheFirstSync()
    {
        inventory = null;
        assertThat(profiler(10).poll()).isZero();
    }

    @Test
    void aMongosHasNoProfiler()
    {
        inventory = new Inventory(Inventory.SHARDED, List.of("sales"), database -> List.of());
        assertThat(profiler(10).poll()).isZero();
        assertThat(directory.resolve("position")).doesNotExist();
    }

    @Test
    void aBatchFullOfEntriesReadsOnFromTheLastOne() throws IOException
    {
        mongo.profile("sales", entry("query", "alice", new Document("find", "orders")), entry("query", "bob", new Document("find", "orders")));
        Profiler profiler = profiler(1);
        profiler.poll();
        assertThat(profiler.poll()).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("position"))).contains(Long.toString(NOW.minusSeconds(5).toEpochMilli()));
    }

    @Test
    void aDatabaseItMayNotReadIsToldOfAndTheOthersGoOn()
    {
        Profiler profiler = profiler(10);
        profiler.poll();
        mongo.failProfile(false);
        assertThat(profiler.poll()).isZero();
    }

    @Test
    void aDeploymentThatCannotBeReachedFailsTheRead()
    {
        Profiler profiler = profiler(10);
        profiler.poll();
        mongo.failProfile(true);
        assertThatThrownBy(profiler::poll).isInstanceOf(MongoFailure.class);
    }

    @Test
    void anUnreadablePositionReadsOnFromThePresent() throws IOException
    {
        Files.writeString(directory.resolve("position"), "not json");
        mongo.profile("sales", entry("query", "alice", new Document("find", "orders")));
        assertThat(profiler(10).poll()).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void aPositionThatCannotBeWrittenIsNotFatal() throws IOException
    {
        Files.createDirectory(directory.resolve("position"));
        Files.writeString(directory.resolve("position").resolve("taken"), "");
        assertThat(profiler(10).poll()).isZero();
    }
}
