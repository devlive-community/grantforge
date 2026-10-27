// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeTest
{
    private static final Logger LOG = Logger.getLogger(AgentRuntimeTest.class.getName());

    @TempDir
    Path directory;

    private AgentRuntime start() throws IOException
    {
        // Nothing listens on the discard port: the GrantForge agent keeps trying in the background.
        Map<String, String> values = SettingsTest.minimal(directory);
        values.put("grantforge.server.url", "http://127.0.0.1:9");
        Settings settings = new Settings(values, "Test");
        return AgentRuntime.start(settings.agent("1"), Duration.ZERO, "Test", LOG, UncheckedIOException.class);
    }

    @Test
    void runsTasksOnItsOwnTimerThreads() throws Exception
    {
        try (AgentRuntime runtime = start()) {
            assertThat(runtime.agent()).isNotNull();
            assertThat(runtime.audit()).isNotNull();
            CountDownLatch turns = new CountDownLatch(3);
            List<String> threads = new CopyOnWriteArrayList<>();
            runtime.every("count", Duration.ZERO, Duration.ofMillis(1), () -> {
                threads.add(Thread.currentThread().getName());
                turns.countDown();
            });
            assertThat(turns.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(threads).allSatisfy(thread -> assertThat(thread).startsWith("grantforge-test-"));
        }
    }

    @Test
    void logsAFailedTurnAndRunsTheNext() throws Exception
    {
        try (Logs logs = Logs.of(LOG); AgentRuntime runtime = start()) {
            CountDownLatch turns = new CountDownLatch(2);
            runtime.every("sync with the policies", Duration.ZERO, Duration.ofMillis(1), () -> {
                turns.countDown();
                throw new UncheckedIOException(new IOException("connection refused"));
            });
            assertThat(turns.await(10, TimeUnit.SECONDS)).as("a failed turn does not stop the timer").isTrue();
            runtime.guarded("read the log", () -> {
                throw new IllegalStateException("broken");
            });
            assertThat(logs.lines()).contains("WARNING The GrantForge Test agent could not sync with the policies: java.io.IOException: connection refused",
                    "SEVERE The GrantForge Test agent could not read the log");
        }
    }

    @Test
    void letsGoOfWhatWasRegisteredAfterTheAgent() throws Exception
    {
        List<String> closed = new CopyOnWriteArrayList<>();
        AgentRuntime runtime = start();
        runtime.closes(() -> closed.add("syncing"));
        runtime.closes(() -> closed.add("reading"));
        CountDownLatch running = new CountDownLatch(1);
        runtime.every("wait", Duration.ZERO, Duration.ofHours(1), running::countDown);
        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
        runtime.close();
        assertThat(closed).containsExactly("syncing", "reading");
    }

    @Test
    void stopsWaitingForTheTimersWhenInterrupted() throws Exception
    {
        List<String> closed = new CopyOnWriteArrayList<>();
        AgentRuntime runtime = start();
        runtime.closes(() -> closed.add("syncing"));
        Thread.currentThread().interrupt();
        try {
            runtime.close();
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept for the caller").isTrue();
        }
        finally {
            assertThat(Thread.interrupted()).isTrue();
        }
        assertThat(closed).containsExactly("syncing");
    }
}
