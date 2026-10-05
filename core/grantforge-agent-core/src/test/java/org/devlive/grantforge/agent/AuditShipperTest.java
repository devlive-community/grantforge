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

import static org.assertj.core.api.Assertions.assertThat;

class AuditShipperTest
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

    private AuditShipper shipper(long spoolLimit)
    {
        return shipper(spoolLimit, cache);
    }

    private AuditShipper shipper(long spoolLimit, Path directory)
    {
        AgentSettings settings = server.settings(directory).spoolLimitBytes(spoolLimit).build();
        return new AuditShipper(settings, new ServerClient(settings));
    }

    private static AccessEvent event(String user)
    {
        return AccessEvent.builder(user, "/data", "read", true).build();
    }

    @Test
    void sendsFullBatchesAndTheRestWhenAsked()
    {
        AuditShipper shipper = shipper(1_000_000);
        shipper.record(event("a"));
        shipper.record(event("b"));
        shipper.record(event("c"));

        shipper.ship(false);
        assertThat(server.eventBatches).hasSize(1);
        assertThat(shipper.waiting()).isEqualTo(1);
        shipper.ship(true);
        assertThat(server.eventBatches).hasSize(2);
        assertThat(server.eventsReceived()).isEqualTo(3);
        assertThat(shipper.waiting()).isZero();
    }

    @Test
    void dropsEventsRatherThanWaitWhenTheQueueIsFull()
    {
        AuditShipper shipper = shipper(1_000_000);
        for (int index = 0; index < 4; index++) {
            assertThat(shipper.record(event("u" + index))).isTrue();
        }

        assertThat(shipper.record(event("late"))).isFalse();
        assertThat(shipper.dropped()).isEqualTo(1);
    }

    @Test
    void spoolsWhileTheServerIsAwayAndSendsLater() throws IOException
    {
        AuditShipper shipper = shipper(1_000_000);
        server.down(true);
        shipper.record(event("a"));
        shipper.record(event("b"));
        shipper.record(event("c"));
        shipper.ship(true);

        assertThat(shipper.spooled()).hasSize(2);
        assertThat(server.eventsReceived()).isZero();
        server.down(false);
        shipper.record(event("d"));
        shipper.ship(true);
        assertThat(server.eventsReceived()).isEqualTo(4);
        assertThat(shipper.spooled()).isEmpty();
        assertThat(shipper.dropped()).isZero();
        // A spooled file that cannot be read is dropped instead of blocking the others.
        Files.createDirectories(cache.resolve(AuditShipper.SPOOL));
        Files.writeString(cache.resolve(AuditShipper.SPOOL).resolve("0-broken.json"), "not json");
        shipper.ship(true);
        assertThat(shipper.spooled()).isEmpty();
    }

    @Test
    void keepsTheSpoolUnderItsLimitByDroppingTheOldest()
    {
        // About one batch of two events fits.
        AuditShipper shipper = shipper(800);
        server.down(true);
        for (int round = 0; round < 3; round++) {
            shipper.record(event("a" + round));
            shipper.record(event("b" + round));
            shipper.ship(true);
        }

        assertThat(shipper.spooled()).hasSize(1);
        assertThat(shipper.dropped()).isEqualTo(4);
        AuditShipper none = shipper(0, cache.resolve("none"));
        none.record(event("x"));
        none.ship(true);
        assertThat(none.dropped()).isEqualTo(1);
    }
}
