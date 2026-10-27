// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LauncherTest
{
    private static final String VERSION = "/META-INF/grantforge/test-agent-version.properties";

    @TempDir
    Path work;

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void loadsUtf8SettingsAndOnlyOverridesGrantForgeKeys() throws IOException
    {
        Path settings = Files.writeString(work.resolve("agent.properties"), "grantforge.instance=代理一\n"
                + "grantforge.sync.interval.ms=10000\ntest.option=from-file\n", StandardCharsets.UTF_8);
        String interval = System.getProperty("grantforge.sync.interval.ms");
        String instance = System.getProperty("grantforge.instance");
        String other = System.getProperty("test.option");
        try {
            System.clearProperty("grantforge.instance");
            System.setProperty("grantforge.sync.interval.ms", "20000");
            System.setProperty("test.option", "from-system");
            Map<String, String> values = Launcher.settings(settings.toString());
            assertThat(values).containsEntry("grantforge.instance", "代理一")
                    .containsEntry("grantforge.sync.interval.ms", "20000").containsEntry("test.option", "from-file");
        }
        finally {
            restore("grantforge.sync.interval.ms", interval);
            restore("grantforge.instance", instance);
            restore("test.option", other);
        }
    }

    @Test
    void reportsAnUnreadableConfigurationInsteadOfUsingDefaults()
    {
        assertThatThrownBy(() -> Launcher.settings(work.resolve("missing.properties").toString())).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void readsTheVersionTheJarShips()
    {
        assertThat(Launcher.version(LauncherTest.class, VERSION)).isEqualTo("1.2.3");
        assertThat(Launcher.version(LauncherTest.class, "/META-INF/grantforge/none.properties")).isEqualTo("unknown");
    }

    @Test
    void refusesToStartWithoutItsSettingsOrWhenTheAgentFails() throws IOException
    {
        try (Logs logs = Logs.of(Logger.getLogger(LauncherTest.class.getName()))) {
            assertThat(Launcher.run(new String[0], "Test", LauncherTest.class, VERSION, (settings, version) -> () -> { })).isEqualTo(Launcher.USAGE);
            assertThat(logs.lines()).containsExactly("SEVERE usage: java -jar grantforge-agent-test.jar <settings.properties>");
            String missing = work.resolve("missing.properties").toString();
            assertThat(Launcher.run(new String[] {missing}, "Test", LauncherTest.class, VERSION, (settings, version) -> () -> { }))
                    .isEqualTo(Launcher.FAILED);
            Path settings = Files.writeString(work.resolve("agent.properties"), "grantforge.instance=one\n");
            assertThat(Launcher.run(new String[] {settings.toString()}, "Test", LauncherTest.class, VERSION, (values, version) -> {
                throw new IllegalArgumentException("missing Test agent setting grantforge.server.url");
            })).isEqualTo(Launcher.FAILED);
            assertThat(logs.lines()).last().isEqualTo("SEVERE The GrantForge Test agent could not start: missing Test agent setting"
                    + " grantforge.server.url");
        }
    }

    @Test
    void runsTheAgentUntilTheProcessStops() throws Exception
    {
        Path settings = Files.writeString(work.resolve("agent.properties"), "grantforge.instance=one\n");
        AtomicReference<String> started = new AtomicReference<>();
        List<String> closed = new CopyOnWriteArrayList<>();
        List<Thread> hooks = new CopyOnWriteArrayList<>();
        CompletableFuture<Integer> status = CompletableFuture.supplyAsync(() -> Launcher.run(new String[] {settings.toString()}, "Test",
                LauncherTest.class, VERSION, (values, version) -> {
                    started.set(values.get("grantforge.instance") + " " + version);
                    return () -> closed.add("agent");
                }, hooks::add));
        while (hooks.isEmpty()) {
            Thread.onSpinWait();
        }
        assertThat(status).isNotDone();
        assertThat(started.get()).isEqualTo("one 1.2.3");
        assertThat(hooks.get(0).getName()).isEqualTo("grantforge-test-stop");
        hooks.get(0).start();
        assertThat(status.get(10, TimeUnit.SECONDS)).isZero();
        assertThat(closed).containsExactly("agent");
    }

    @Test
    void stopsEvenWhenTheAgentDoesNotCloseCleanly() throws Exception
    {
        Path settings = Files.writeString(work.resolve("agent.properties"), "grantforge.instance=one\n");
        List<Thread> hooks = new CopyOnWriteArrayList<>();
        try (Logs logs = Logs.of(Logger.getLogger(LauncherTest.class.getName()))) {
            CompletableFuture<Integer> status = CompletableFuture.supplyAsync(() -> Launcher.run(new String[] {settings.toString()}, "Test",
                    LauncherTest.class, VERSION, (values, version) -> () -> {
                        throw new IOException("connection reset");
                    }, hooks::add));
            while (hooks.isEmpty()) {
                Thread.onSpinWait();
            }
            hooks.get(0).start();
            assertThat(status.get(10, TimeUnit.SECONDS)).isZero();
            assertThat(logs.lines()).contains("WARNING The GrantForge Test agent did not stop cleanly");
        }
    }

    private static void restore(String key, @Nullable String value)
    {
        if (value == null) {
            System.clearProperty(key);
        }
        else {
            System.setProperty(key, value);
        }
    }
}
