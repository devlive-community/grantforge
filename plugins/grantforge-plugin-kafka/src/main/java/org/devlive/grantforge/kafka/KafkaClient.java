// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

/**
 * Runs work against a Kafka cluster through an AdminClient opened for it and closed after it. Kafka and JAAS load
 * login modules and callback handlers through the thread's context class loader, so the work runs with the plugin's.
 * Failures are named by their kind: a refused sign-in, missing permissions, or brokers that cannot be reached.
 */
final class KafkaClient
{
    private static final AtomicLong CLIENTS = new AtomicLong();

    /** Opens an AdminClient. */
    @FunctionalInterface
    interface Opener
    {
        /**
         * Opens a client.
         *
         * @param config its settings
         * @return the client
         */
        Admin open(Map<String, Object> config);
    }

    /**
     * Work with an open client.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param admin the client
         * @return the result
         * @throws ExecutionException when Kafka answers a request with an error
         * @throws InterruptedException when the thread is interrupted while waiting
         */
        T run(Admin admin) throws ExecutionException, InterruptedException;
    }

    private final KafkaSettings settings;
    private final Opener opener;

    KafkaClient(KafkaSettings settings)
    {
        this(settings, Admin::create);
    }

    KafkaClient(KafkaSettings settings, Opener opener)
    {
        this.settings = requireNonNull(settings, "settings");
        this.opener = requireNonNull(opener, "opener");
    }

    /**
     * Opens a client, runs the work and closes the client.
     *
     * @param work the work
     * @param <T> what it returns
     * @return the result
     * @throws LookupException naming why it failed
     */
    // The work runs with the plugin's class loader as the context one, for JAAS and Kafka to find their classes; the client
    // is closed with a bound, which try-with-resources would not give.
    @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.UseProperClassLoader", "PMD.UseTryWithResources"})
    <T> T run(Work<T> work)
    {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(KafkaClient.class.getClassLoader());
        try {
            Admin admin = opener.open(settings.client(KerberosPasswordHandler.class, "grantforge-lookup-" + CLIENTS.incrementAndGet()));
            try {
                return work.run(admin);
            }
            finally {
                admin.close(Duration.ofMillis(Math.min(settings.timeout(), 5000)));
            }
        }
        catch (ExecutionException failed) {
            throw failure(failed);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LookupException(LookupException.Reason.FAILED, "interrupted while waiting for Kafka", interrupted);
        }
        catch (IOException | KafkaException failed) {
            throw failure(failed);
        }
        finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * Names why something failed, from the first failure in the chain that tells.
     *
     * @param failed the failure
     * @return the lookup failure
     */
    static LookupException failure(Throwable failed)
    {
        if (failed instanceof LookupException lookup) {
            return lookup;
        }
        // Kafka's futures wrap what the broker answered; that is the part worth showing.
        Throwable answer = failed instanceof ExecutionException && failed.getCause() != null ? failed.getCause() : failed;
        String text = message(answer);
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            LookupException.@Nullable Reason reason = reason(cause);
            if (reason != null) {
                return new LookupException(reason, text, failed);
            }
        }
        return new LookupException(LookupException.Reason.FAILED, text, failed);
    }

    private static LookupException.@Nullable Reason reason(Throwable cause)
    {
        if (cause instanceof AuthenticationException || cause instanceof LoginException || cause instanceof SaslException) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if (cause instanceof AuthorizationException) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        // Brokers whose certificates cannot be verified are as good as unreachable: nothing may be asked of them.
        if (cause instanceof TimeoutException || cause instanceof ConnectException || cause instanceof UnknownHostException
                || cause instanceof SSLException) {
            return LookupException.Reason.UNREACHABLE;
        }
        return null;
    }

    /**
     * Returns the first line of a failure's message, which is the useful part.
     *
     * @param failure the failure
     * @return the line
     */
    static String message(Throwable failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return line < 0 ? text : text.substring(0, line);
    }
}
