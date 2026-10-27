// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class SyncLoopTest
{
    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();
    private final Recording sync = new Recording(snapshot);

    /** A sync whose system is a list of what it was asked to do. */
    static final class Recording
            extends SyncLoop<String, Integer>
    {
        final List<String> calls = new ArrayList<>();
        int changes;

        Recording(AtomicReference<@Nullable Snapshot> snapshot)
        {
            super(new Target("Test", "test", "Test's own grants"), snapshot::get, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        protected String read(Snapshot snapshot)
        {
            calls.add("read " + snapshot.service());
            return "state";
        }

        @Override
        protected Integer apply(String state, @Nullable Snapshot snapshot, Instant now)
        {
            calls.add("apply " + state + " " + (snapshot == null ? "nothing" : snapshot.service()) + " at " + now);
            return changes;
        }

        @Override
        protected boolean changed(Integer outcome)
        {
            return outcome > 0;
        }

        @Override
        protected String summary(Integer outcome)
        {
            return outcome + " changes";
        }
    }

    private static Snapshot snapshot(String type, boolean enabled, long version)
    {
        String json = "{\"format\":1,\"service\":\"warehouse\",\"serviceType\":\"" + type + "\",\"serviceEnabled\":" + enabled
                + ",\"policyVersion\":" + version + ",\"definition\":{\"resources\":[{\"name\":\"database\",\"parent\":null,"
                + "\"matcher\":\"WILDCARD\",\"caseSensitive\":true}],\"accessTypes\":[{\"name\":\"read\",\"impliedGrants\":[]}],"
                + "\"conditions\":[]},\"policies\":[],\"roles\":{},\"groups\":{}}";
        return Snapshot.parse(json.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    private Logs logs()
    {
        return Logs.of(Logger.getLogger(Recording.class.getName()));
    }

    @Test
    void leavesTheSystemAloneUntilTheFirstSnapshot()
    {
        try (Logs logs = logs()) {
            assertThat(sync.run()).isNull();
            assertThat(sync.run()).isNull();
            assertThat(sync.calls).isEmpty();
            assertThat(logs.lines()).containsExactly("INFO The GrantForge Test agent waits for its first policy snapshot; Test is left as it is");
        }
    }

    @Test
    void appliesTheSnapshotAtThePresentAndSaysWhatChanged()
    {
        try (Logs logs = logs()) {
            snapshot.set(snapshot("test", true, 4));
            sync.changes = 2;
            assertThat(sync.run()).isEqualTo(2);
            sync.changes = 0;
            assertThat(sync.run()).isZero();
            snapshot.set(snapshot("test", true, 5));
            sync.run();
            assertThat(sync.calls).containsExactly("read warehouse", "apply state warehouse at " + NOW, "read warehouse",
                    "apply state warehouse at " + NOW, "read warehouse", "apply state warehouse at " + NOW);
            assertThat(logs.lines()).as("a turn that changes nothing under the same version says nothing").containsExactly(
                    "INFO GrantForge policy version 4 applied to Test: 2 changes", "INFO GrantForge policy version 5 applied to Test: 0 changes");
        }
    }

    @Test
    void grantsNothingForAServiceOfAnotherTypeOrNotInUse()
    {
        try (Logs logs = logs()) {
            snapshot.set(snapshot("hive", true, 1));
            sync.run();
            sync.run();
            snapshot.set(snapshot("test", false, 1));
            sync.run();
            snapshot.set(snapshot("test", true, 1));
            sync.run();
            snapshot.set(snapshot("hive", true, 1));
            sync.run();
            assertThat(sync.calls).filteredOn(call -> call.startsWith("apply")).containsExactly("apply state nothing at " + NOW,
                    "apply state nothing at " + NOW, "apply state nothing at " + NOW, "apply state warehouse at " + NOW,
                    "apply state nothing at " + NOW);
            assertThat(logs.lines()).filteredOn(line -> line.startsWith("SEVERE")).as("each state is said once, and again after it ended")
                    .containsExactly("SEVERE The agent's token is bound to a hive service, not a Test one; GrantForge grants nothing",
                            "SEVERE The Test service warehouse is not in use; GrantForge grants nothing and Test's own grants apply",
                            "SEVERE The agent's token is bound to a hive service, not a Test one; GrantForge grants nothing");
        }
    }

    @Test
    void namesTheSystemWithItsArticle()
    {
        assertThat(SyncLoop.article("OpenSearch")).isEqualTo("an");
        assertThat(SyncLoop.article("ClickHouse")).isEqualTo("a");
        assertThat(SyncLoop.article("elasticsearch")).isEqualTo("an");
        assertThat(SyncLoop.article("")).isEqualTo("a");
    }
}
