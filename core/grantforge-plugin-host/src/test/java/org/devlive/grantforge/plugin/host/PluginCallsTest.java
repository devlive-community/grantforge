// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginCallsTest
{
    @Test
    void callsWithThePluginsClassLoaderAndATimeLimit()
    {
        ClassLoader plugin = new ClassLoader(null)
        {
        };
        AtomicReference<ClassLoader> seen = new AtomicReference<>();
        try (PluginCalls calls = new PluginCalls(Duration.ofMillis(300))) {
            assertThat(calls.call("demo", plugin, () -> {
                seen.set(Thread.currentThread().getContextClassLoader());
                return 42;
            })).isEqualTo(42);
            assertThat(seen.get()).isSameAs(plugin);
            assertThatThrownBy(() -> calls.call("demo", plugin, () -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(PluginCallException.class).hasMessage("demo failed: java.lang.IllegalStateException: boom")
                    .hasCauseInstanceOf(IllegalStateException.class).satisfies(failure -> assertThat(kindOf(failure))
                            .isEqualTo(PluginCallException.Kind.FAILED));
            assertThatThrownBy(() -> calls.call("demo", plugin, () -> {
                Thread.sleep(5_000);
                return 0;
            })).isInstanceOf(PluginCallException.class).hasMessage("demo did not answer within 0s")
                    .satisfies(failure -> assertThat(kindOf(failure)).isEqualTo(PluginCallException.Kind.TIMED_OUT));
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> calls.call("demo", plugin, () -> 1)).isInstanceOf(PluginCallException.class)
                    .hasMessage("demo was interrupted")
                    .satisfies(failure -> assertThat(kindOf(failure)).isEqualTo(PluginCallException.Kind.INTERRUPTED));
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    private static PluginCallException.Kind kindOf(Throwable failure)
    {
        return ((PluginCallException) failure).getKind();
    }
}
