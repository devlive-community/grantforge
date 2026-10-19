// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A running Trino agent around a stand-in for the signed-policy agent, whose snapshot the tests set, and which sends
 * every access event at once so that the tests see them.
 */
final class TestRuntime
{
    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();
    private final TrinoAgentRuntime runtime;

    TestRuntime(@Nullable Snapshot initial)
    {
        snapshot.set(initial);
        when(agent.snapshot()).thenAnswer(call -> snapshot.get());
        try {
            runtime = TrinoAgentRuntime.start(settings(Files.createTempDirectory("grantforge-trino")), "test", settings -> agent, () -> 0L);
        }
        catch (IOException broken) {
            throw new UncheckedIOException(broken);
        }
    }

    /**
     * The settings of an agent with a token file in a directory, which sends every access event at once.
     *
     * @param directory where the token and the cache go
     * @return the settings
     * @throws IOException if the token cannot be written
     */
    static Map<String, String> settings(Path directory) throws IOException
    {
        Path token = directory.resolve("token");
        Files.writeString(token, " gfa_token\n", StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.token.file", token.toString());
        values.put("grantforge.server.url", "https://grantforge.example.com/");
        values.put("grantforge.instance", "coordinator-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        values.put("grantforge.audit.fold.window.ms", "0");
        return values;
    }

    TrinoAgentRuntime runtime()
    {
        return runtime;
    }

    GrantForgeAgent agent()
    {
        return agent;
    }

    void snapshot(@Nullable Snapshot next)
    {
        snapshot.set(next);
    }

    /** The access events sent so far. */
    List<AccessEvent> events()
    {
        ArgumentCaptor<AccessEvent> sent = ArgumentCaptor.forClass(AccessEvent.class);
        try {
            verify(agent, atLeast(0)).record(sent.capture());
        }
        catch (AssertionError none) {
            return List.of();
        }
        return sent.getAllValues();
    }
}
