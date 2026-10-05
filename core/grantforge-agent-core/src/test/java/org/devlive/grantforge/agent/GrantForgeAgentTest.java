// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class GrantForgeAgentTest
{
    @TempDir
    Path cache;

    private FakeServer server;

    @BeforeEach
    void start() throws IOException
    {
        server = new FakeServer();
    }

    @AfterEach
    void stop()
    {
        server.close();
    }

    private GrantForgeAgent agent(AgentSettings settings)
    {
        return new GrantForgeAgent(settings, Map.of());
    }

    private static long version(GrantForgeAgent agent)
    {
        return requireNonNull(agent.snapshot(), "no snapshot applied").policyVersion();
    }

    private static AgentDecision analystSelects(GrantForgeAgent agent)
    {
        return agent.decide(Snapshots.request("alice", "select", "sales", "orders"));
    }

    @Test
    void decidesNothingUntilASnapshotIsApplied()
    {
        GrantForgeAgent agent = agent(server.settings(cache).build());

        assertThat(agent.snapshot()).isNull();
        assertThat(analystSelects(agent).outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(analystSelects(agent).policyVersion()).isNull();
    }

    @Test
    void appliesSnapshotsAsThePolicyVersionChanges()
    {
        GrantForgeAgent agent = agent(server.settings(cache).build());
        server.refreshSeconds(15);

        assertThat(agent.refresh()).isEqualTo(15);
        assertThat(analystSelects(agent).allowed()).isTrue();
        assertThat(server.keyRequests.get()).isEqualTo(1);
        assertThat(Files.exists(cache.resolve(SnapshotStore.BODY))).isTrue();

        agent.refresh();
        assertThat(server.downloads.get()).isEqualTo(1);
        assertThat(server.heartbeats.get(1).path("appliedPolicyVersion").asLong()).isEqualTo(1);

        server.publish(Snapshots.json(2, false), 2);
        agent.refresh();
        assertThat(version(agent)).isEqualTo(2);
        assertThat(analystSelects(agent).outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(server.keyRequests.get()).isEqualTo(1);
        assertThat(agent.serverReachable()).isTrue();
    }

    @Test
    void anUnchangedSnapshotIsNotDownloadedAgain()
    {
        GrantForgeAgent agent = agent(server.settings(cache).build());
        agent.refresh();
        // The server's version moves while the content stays: the ETag answers 304.
        server.publish(Snapshots.json(1, true), 9);

        agent.refresh();
        assertThat(server.downloads.get()).isEqualTo(1);
        assertThat(version(agent)).isEqualTo(1);
    }

    @Test
    void keepsThePoliciesItHasWhenASnapshotIsRejected()
    {
        GrantForgeAgent agent = agent(server.settings(cache).build());
        agent.refresh();

        server.tamper(true);
        server.publish(Snapshots.json(2, true), 2);
        assertThat(agent.refresh()).isEqualTo(7);
        assertThat(version(agent)).isEqualTo(1);

        server.tamper(false);
        server.publish("{\"format\": 99}", 3);
        agent.refresh();
        assertThat(version(agent)).isEqualTo(1);
        assertThat(analystSelects(agent).allowed()).isTrue();
    }

    @Test
    void followsAKeyTheServerRotatedUnlessOneIsPinned()
    {
        GrantForgeAgent agent = agent(server.settings(cache).build());
        agent.refresh();
        server.rotateKey();
        server.publish(Snapshots.json(2, true), 2);

        agent.refresh();
        assertThat(version(agent)).isEqualTo(2);
        assertThat(server.keyRequests.get()).isEqualTo(2);

        SigningKey other = SigningKey.of(FakeServer.publicKey(FakeServer.keyPair()));
        GrantForgeAgent pinned = agent(server.settings(cache.resolve("pinned")).trustedKey(other).build());
        pinned.refresh();
        assertThat(pinned.snapshot()).isNull();
    }

    @Test
    void startsWithTheStoredSnapshotWhileTheServerIsAway()
    {
        agent(server.settings(cache).build()).refresh();
        server.down(true);

        GrantForgeAgent restarted = agent(server.settings(cache).build());
        restarted.loadStored();
        assertThat(analystSelects(restarted).allowed()).isTrue();
        assertThat(restarted.refresh()).isEqualTo(7);
        assertThat(restarted.serverReachable()).isFalse();
        restarted.refresh();
        assertThat(version(restarted)).isEqualTo(1);

        GrantForgeAgent pinnedElsewhere = agent(server.settings(cache).trustedKey(SigningKey.of(FakeServer.publicKey(FakeServer.keyPair())))
                .build());
        pinnedElsewhere.loadStored();
        assertThat(pinnedElsewhere.snapshot()).isNull();
    }

    @Test
    void anUnreadableStoredSnapshotIsSkipped() throws IOException
    {
        agent(server.settings(cache).build()).refresh();
        Files.writeString(cache.resolve(SnapshotStore.META), Files.readString(cache.resolve(SnapshotStore.META)));
        Files.writeString(cache.resolve(SnapshotStore.BODY), "{\"format\": 99}");
        GrantForgeAgent agent = agent(server.settings(cache).build());

        agent.loadStored();
        assertThat(agent.snapshot()).isNull();
    }

    @Test
    void runsInTheBackgroundAndShipsEventsWhenClosed() throws Exception
    {
        AgentSettings settings = server.settings(cache).audit(2, Duration.ofSeconds(60), 10).build();
        try (GrantForgeAgent agent = GrantForgeAgent.start(settings, Map.of())) {
            long deadline = System.currentTimeMillis() + 10_000;
            while (agent.snapshot() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(analystSelects(agent).allowed()).isTrue();
            assertThat(agent.record(AccessEvent.builder("alice", "sales.orders.id", "select", true).decidedBy(analystSelects(agent))
                    .build())).isTrue();
            assertThat(agent.droppedEvents()).isZero();
        }
        assertThat(server.eventsReceived()).isEqualTo(1);
        assertThat(server.eventBatches.get(0).path("events").get(0).path("enforcer").asText()).isEqualTo("GRANTFORGE");
    }

    @Test
    void shipsEventsOnItsOwnEveryFlushInterval() throws Exception
    {
        try (GrantForgeAgent agent = GrantForgeAgent.start(server.settings(cache).build(), Map.of())) {
            agent.record(AccessEvent.builder("bob", "/data", "read", false).build());
            long deadline = System.currentTimeMillis() + 10_000;
            while (server.eventsReceived() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(server.eventsReceived()).isEqualTo(1);
        }
    }
}
