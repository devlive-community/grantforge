// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ships access events to the server in batches. Recording never blocks the system: an event goes into a bounded queue,
 * and when the queue is full it is dropped and counted. {@link #ship} sends what waits; a batch the server cannot take
 * is written to the spool directory and sent again later, oldest first, while the spool stays under its limit. Events
 * carry ids, so a batch sent twice is stored once.
 */
final class AuditShipper
{
    static final String SPOOL = "audit-spool";

    private static final Logger LOG = Logger.getLogger(AuditShipper.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, @Nullable Object>>> BATCH = new TypeReference<List<Map<String, @Nullable Object>>>()
    {
    };

    private final AgentSettings settings;
    private final ServerClient client;
    private final BlockingQueue<AccessEvent> queue;
    private final Path spool;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong spoolSequence = new AtomicLong();

    AuditShipper(AgentSettings settings, ServerClient client)
    {
        this.settings = settings;
        this.client = client;
        this.queue = new ArrayBlockingQueue<>(settings.auditQueueCapacity());
        this.spool = settings.cacheDirectory().resolve(SPOOL);
    }

    /**
     * Queues an event.
     *
     * @param event the event
     * @return {@code false} if the queue was full and the event was dropped
     */
    boolean record(AccessEvent event)
    {
        if (queue.offer(event)) {
            return true;
        }
        dropped.incrementAndGet();
        return false;
    }

    /**
     * Returns how many events were dropped because the queue was full or the spool over its limit.
     *
     * @return the count
     */
    long dropped()
    {
        return dropped.get();
    }

    /**
     * Returns how many events wait in memory.
     *
     * @return the count
     */
    int waiting()
    {
        return queue.size();
    }

    /**
     * Sends the events that wait, in batches; while the server takes them, also sends the spooled batches. A batch the
     * server does not take is spooled, and shipping stops until the next call.
     *
     * @param everything {@code true} to send every waiting event, {@code false} to leave a last partial batch for later
     *        unless the flush interval has passed (the caller decides that by calling with {@code true})
     */
    // A list per batch is what batching is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    void ship(boolean everything)
    {
        boolean reachable = true;
        while (queue.size() >= settings.auditBatchSize() || everything && !queue.isEmpty()) {
            List<AccessEvent> events = new ArrayList<>(settings.auditBatchSize());
            queue.drainTo(events, settings.auditBatchSize());
            List<Map<String, @Nullable Object>> batch = new ArrayList<>(events.size());
            for (AccessEvent event : events) {
                batch.add(event.fields());
            }
            if (reachable) {
                reachable = send(batch);
            }
            if (!reachable) {
                spool(batch);
            }
        }
        if (reachable) {
            resend();
        }
    }

    private boolean send(List<Map<String, @Nullable Object>> batch)
    {
        try {
            client.send(batch);
            return true;
        }
        catch (IOException unreachable) {
            LOG.log(Level.FINE, "access events could not be sent; they are spooled", unreachable);
            return false;
        }
    }

    /** Sends the spooled batches, oldest first, until one fails. */
    private void resend()
    {
        for (Path file : spooled()) {
            List<Map<String, @Nullable Object>> batch;
            try {
                batch = JSON.readValue(file.toFile(), BATCH);
            }
            catch (IOException broken) {
                LOG.log(Level.WARNING, "the spooled access events in " + file + " cannot be read; they are dropped", broken);
                delete(file);
                continue;
            }
            if (!send(batch)) {
                return;
            }
            delete(file);
        }
    }

    private void spool(List<Map<String, @Nullable Object>> batch)
    {
        try {
            Files.createDirectories(spool);
            byte[] bytes = JSON.writeValueAsBytes(batch);
            List<Path> files = spooled();
            long used = 0;
            for (Path file : files) {
                used += Files.size(file);
            }
            // The oldest batches make room; a batch larger than the whole spool is not kept.
            for (Path file : files) {
                if (used + bytes.length <= settings.spoolLimitBytes()) {
                    break;
                }
                used -= Files.size(file);
                dropped.addAndGet(JSON.readValue(file.toFile(), BATCH).size());
                delete(file);
            }
            if (used + bytes.length > settings.spoolLimitBytes()) {
                dropped.addAndGet(batch.size());
                return;
            }
            String name = String.format("%019d-%06d.json", System.currentTimeMillis(), spoolSequence.incrementAndGet() % 1_000_000);
            Path temporary = spool.resolve(name + ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, spool.resolve(name));
        }
        catch (IOException failed) {
            dropped.addAndGet(batch.size());
            LOG.log(Level.WARNING, batch.size() + " access events could neither be sent nor spooled; they are lost", failed);
        }
    }

    /** The spooled batches, oldest first. */
    List<Path> spooled()
    {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(spool, "*.json")) {
            for (Path entry : entries) {
                files.add(entry);
            }
        }
        catch (NoSuchFileException missing) {
            return Collections.emptyList();
        }
        catch (IOException unreadable) {
            LOG.log(Level.WARNING, "the audit spool " + spool + " cannot be read", unreadable);
            return Collections.emptyList();
        }
        Collections.sort(files);
        return files;
    }

    private static void delete(Path file)
    {
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException stuck) {
            LOG.log(Level.WARNING, "the spooled access events in " + file + " cannot be deleted", stuck);
        }
    }
}
