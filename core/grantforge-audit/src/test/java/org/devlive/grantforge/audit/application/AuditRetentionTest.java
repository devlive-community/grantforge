// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.csv.Csv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditRetentionTest
{
    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @TempDir
    private Path directory;

    @AfterEach
    void deleteRows()
    {
        events.deleteAllInBatch();
    }

    private void event(Instant at, String reason)
    {
        events.save(AuditEvent.of(at, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, 1L, 7L, "alice", null, reason, "10.0.0.1",
                "Firefox", "req"));
    }

    private AuditRetention retention(String archive)
    {
        return new AuditRetention(events, transactionManager, CLOCK, Duration.ofDays(30), archive);
    }

    private static List<List<String>> read(Path file) throws IOException
    {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return Csv.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void archivesAndRemovesOnlyEventsPastTheRetentionPeriod() throws IOException
    {
        IntStream.range(0, AuditRetention.BATCH + 5).forEach(i -> event(NOW.minus(Duration.ofDays(40)).plusSeconds(i), "old, " + i));
        event(NOW.minus(Duration.ofDays(10)), "recent");

        assertThat(retention(directory.resolve("archive").toString()).purge()).isEqualTo(AuditRetention.BATCH + 5);

        assertThat(events.findAll()).extracting(AuditEvent::getReason).containsExactly("recent");
        List<Path> files;
        try (Stream<Path> listed = Files.list(directory.resolve("archive"))) {
            files = listed.sorted().toList();
        }
        assertThat(files).extracting(file -> file.getFileName().toString()).containsExactly("audit-20261003T080000-1.csv.gz",
                "audit-20261003T080000-2.csv.gz");
        List<List<String>> first = read(files.get(0));
        assertThat(first.get(0)).isEqualTo(AuditRetention.COLUMNS);
        assertThat(first).hasSize(AuditRetention.BATCH + 1);
        assertThat(first.get(1)).containsSubsequence("LOGIN_FAILED", "FAILURE", "1", "7", "alice", "", "old, 0", "10.0.0.1", "Firefox");
        assertThat(read(files.get(1))).hasSize(6);
        assertThat(retention("").purge()).isZero();
    }

    @Test
    void removesWithoutArchivingUnlessADirectoryIsSetAndKeepsWhatCannotBeArchived() throws IOException
    {
        event(NOW.minus(Duration.ofDays(40)), "old");
        Path blocked = Files.writeString(directory.resolve("file"), "not a directory");
        AuditRetention unwritable = retention(blocked.toString());
        assertThatThrownBy(unwritable::purge).isInstanceOf(UncheckedIOException.class);
        unwritable.scheduled();
        assertThat(events.count()).isOne();

        retention(" ").scheduled();
        assertThat(events.count()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new AuditRetention(events, transactionManager, CLOCK, Duration.ZERO, null));
    }
}
