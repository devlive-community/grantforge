// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.csv.Csv;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Removes audit events past the retention period ({@code grantforge.audit.retention}, a year unless configured) now and
 * then ({@code grantforge.audit.purge-interval}, daily). With {@code grantforge.audit.archive-directory} set, each batch is
 * first written there as a gzip-compressed CSV file, and nothing is removed that could not be written. Several nodes may
 * purge at once; they only remove the same rows, though each may archive them.
 */
@Component
public final class AuditRetention
{
    /** How many old events one batch archives and removes. */
    static final int BATCH = 1000;

    /** The columns of an archive file. */
    static final List<String> COLUMNS = List.of("id", "occurredAt", "action", "outcome", "tenantId", "actorId", "actorName",
            "targetId", "reason", "clientIp", "userAgent", "requestId");

    private static final Logger LOG = LoggerFactory.getLogger(AuditRetention.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    private final AuditEventRepository events;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration retention;
    private final @Nullable Path archive;

    /**
     * Creates the job.
     *
     * @param events the event store
     * @param transactionManager opens transactions
     * @param clock the current time
     * @param retention how long events are kept
     * @param archive where removed events are written first; blank to remove them without archiving
     * @throws IllegalArgumentException if the retention period is not positive
     */
    // No archive directory is null: events are then removed without archiving.
    @SuppressWarnings("PMD.NullAssignment")
    public AuditRetention(AuditEventRepository events, PlatformTransactionManager transactionManager, Clock clock,
            @Value("${grantforge.audit.retention:365d}") Duration retention,
            @Value("${grantforge.audit.archive-directory:}") @Nullable String archive)
    {
        this.events = requireNonNull(events, "events");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("grantforge.audit.retention must be positive");
        }
        this.retention = retention;
        this.archive = archive == null || archive.isBlank() ? null : Path.of(archive.strip());
    }

    /** Purges old events on schedule. */
    @Scheduled(initialDelayString = "${grantforge.audit.purge-delay:10m}", fixedDelayString = "${grantforge.audit.purge-interval:1d}")
    public void scheduled()
    {
        try {
            int removed = purge();
            if (removed > 0) {
                LOG.info("Removed {} audit events older than {}", removed, retention);
            }
        }
        catch (UncheckedIOException unwritable) {
            LOG.warn("Kept the audit events past their retention period: the archive could not be written", unwritable);
        }
    }

    /**
     * Archives, if configured, and removes the events past the retention period, a batch at a time.
     *
     * @return how many events were removed
     * @throws UncheckedIOException if an archive file cannot be written; its batch and later ones stay
     */
    public int purge()
    {
        Instant before = clock.instant().minus(retention);
        String stamp = STAMP.format(clock.instant());
        int removed = 0;
        for (int batch = 1; ; batch++) {
            int number = batch;
            Integer done = transactions.execute(status -> {
                List<AuditEvent> old = events.findOlderThan(before, PageRequest.of(0, BATCH));
                if (!old.isEmpty()) {
                    archive(old, stamp, number);
                    events.deleteAll(old);
                }
                return old.size();
            });
            int count = requireNonNullElse(done, 0);
            removed += count;
            if (count < BATCH) {
                return removed;
            }
        }
    }

    private void archive(List<AuditEvent> old, String stamp, int batch)
    {
        Path directory = archive;
        if (directory == null) {
            return;
        }
        List<List<String>> records = new ArrayList<>();
        records.add(COLUMNS);
        old.forEach(event -> records.add(row(event)));
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("audit-" + stamp + "-" + batch + ".csv.gz");
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE_NEW))) {
                out.write(Csv.write(records).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException failed) {
            throw new UncheckedIOException("cannot archive audit events to " + directory, failed);
        }
    }

    private static List<String> row(AuditEvent event)
    {
        return List.of(Long.toString(event.requireId()), event.getOccurredAt().toString(), event.getAction().name(),
                event.getOutcome().name(), text(event.getTenantId()), text(event.getActorId()), text(event.getActorName()),
                text(event.getTargetId()), text(event.getReason()), text(event.getClientIp()), text(event.getUserAgent()),
                text(event.getRequestId()));
    }

    private static String text(@Nullable Object value)
    {
        return value == null ? "" : value.toString();
    }
}
