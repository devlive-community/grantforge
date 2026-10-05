// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * Calls into plugin code with a time limit and with the plugin's class loader as the thread's context class loader,
 * turning anything the plugin throws, or a call that takes too long, into a {@link PluginCallException}.
 */
// Bounding the time plugin code may take needs a thread of its own to abandon.
@SuppressWarnings("PMD.DoNotUseThreads")
public final class PluginCalls
        implements AutoCloseable
{
    private final Duration limit;
    private final ExecutorService threads;

    /**
     * Creates the caller.
     *
     * @param limit how long one call may take
     */
    public PluginCalls(Duration limit)
    {
        this.limit = requireNonNull(limit, "limit");
        AtomicInteger counter = new AtomicInteger();
        this.threads = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "plugin-call-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Calls plugin code.
     *
     * @param pluginId the plugin, for the message of a failure
     * @param loader the plugin's class loader, set as the context class loader during the call
     * @param call the call
     * @param <T> what it returns
     * @return what the call returned
     * @throws PluginCallException if the call throws, takes longer than the limit or is interrupted
     */
    // The plugin's own exception is the useful cause; the ExecutionException only wraps it.
    @SuppressWarnings("PMD.PreserveStackTrace")
    public <T> T call(String pluginId, ClassLoader loader, Callable<T> call)
    {
        // A caller interrupted already gets no call: a quick call could finish before get() looks at the interrupt.
        if (Thread.currentThread().isInterrupted()) {
            throw new PluginCallException(pluginId + " was interrupted", new InterruptedException());
        }
        Future<T> result = threads.submit(() -> {
            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();
            current.setContextClassLoader(loader);
            try {
                return call.call();
            }
            finally {
                current.setContextClassLoader(previous);
            }
        });
        try {
            return result.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException late) {
            result.cancel(true);
            throw new PluginCallException(pluginId + " did not answer within " + limit.toSeconds() + "s", late);
        }
        catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new PluginCallException(pluginId + " failed: " + cause, cause);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PluginCallException(pluginId + " was interrupted", interrupted);
        }
    }

    @Override
    public void close()
    {
        threads.shutdownNow();
    }
}
