// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.auth.oauth2.AuthenticationFactoryOAuth2;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

/**
 * Runs work against a Pulsar cluster through an admin client opened for it and closed after it, signed in as the lookup
 * role. Failures are named by their kind: a refused sign-in, missing permissions, a broker that cannot be reached, or
 * something that does not exist.
 */
final class PulsarAdminClient
{
    private static final String BASIC_PLUGIN = "org.apache.pulsar.client.impl.auth.AuthenticationBasic";

    /** Opens an admin client; Pulsar's own, or a stand-in in tests. */
    @FunctionalInterface
    interface Opener
    {
        /**
         * Opens an admin client.
         *
         * @param settings the service's settings
         * @return the client
         * @throws PulsarClientException when it cannot be built, such as for a certificate that cannot be read
         */
        PulsarAdmin open(PulsarSettings settings) throws PulsarClientException;
    }

    /**
     * Work with Pulsar's admin API.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param admin the open client
         * @return the result
         * @throws PulsarAdminException when Pulsar fails a request
         */
        T run(PulsarAdmin admin) throws PulsarAdminException;
    }

    private final PulsarSettings settings;
    private final Opener opener;

    /**
     * Creates a client of a service with Pulsar's own admin client.
     *
     * @param settings the service's settings
     */
    PulsarAdminClient(PulsarSettings settings)
    {
        this(settings, PulsarAdminClient::open);
    }

    PulsarAdminClient(PulsarSettings settings, Opener opener)
    {
        this.settings = requireNonNull(settings, "settings");
        this.opener = requireNonNull(opener, "opener");
    }

    /**
     * Runs work with an admin client opened for it.
     *
     * @param work the work
     * @param <T> what it returns
     * @return its result
     * @throws LookupException when the work fails, naming why
     */
    <T> T run(Work<T> work)
    {
        try (PulsarAdmin admin = opener.open(settings)) {
            return work.run(admin);
        }
        catch (PulsarAdminException | PulsarClientException | RuntimeException failed) {
            throw failure(failed);
        }
    }

    /**
     * Opens Pulsar's admin client for a service: its brokers, its sign-in, its trusted certificates and its timeout, with
     * the plugin's class loader for what the client loads by name.
     *
     * @param settings the settings
     * @return the client
     * @throws PulsarClientException when it cannot be built
     */
    // The client loads its sign-in plugins by name, from the plugin's own jars; the client it builds closes the sign-in.
    @SuppressWarnings({"PMD.UseProperClassLoader", "PMD.CloseResource"})
    static PulsarAdmin open(PulsarSettings settings) throws PulsarClientException
    {
        PulsarAdminBuilder builder = PulsarAdmin.builder().serviceHttpUrl(settings.webServiceUrl())
                .setContextClassLoader(PulsarAdminClient.class.getClassLoader()).enableTlsHostnameVerification(settings.verifyHostname())
                .connectionTimeout(Math.min(settings.timeout(), 20_000), TimeUnit.MILLISECONDS).readTimeout(settings.timeout(), TimeUnit.MILLISECONDS)
                .requestTimeout(settings.timeout(), TimeUnit.MILLISECONDS);
        if (settings.trustCerts() != null) {
            builder.tlsTrustCertsFilePath(settings.trustCerts());
        }
        Authentication authentication = authentication(settings);
        if (authentication != null) {
            builder.authentication(authentication);
        }
        return builder.build();
    }

    /**
     * Returns how the admin client signs in.
     *
     * @param settings the settings
     * @return the sign-in, or {@code null} for none
     * @throws PulsarClientException when Pulsar has no such sign-in
     */
    static @Nullable Authentication authentication(PulsarSettings settings) throws PulsarClientException
    {
        return switch (settings.authentication()) {
            case PulsarSettings.TOKEN -> AuthenticationFactory.token(String.valueOf(settings.token()));
            case PulsarSettings.BASIC -> AuthenticationFactory.create(BASIC_PLUGIN,
                    Map.of("userId", String.valueOf(settings.user()), "password", String.valueOf(settings.password())));
            case PulsarSettings.OAUTH2 -> {
                AuthenticationFactoryOAuth2.ClientCredentialsBuilder oauth2 = AuthenticationFactoryOAuth2.clientCredentialsBuilder()
                        .issuerUrl(requireNonNull(settings.issuerUrl(), "issuerUrl")).credentialsUrl(requireNonNull(settings.credentialsUrl(), "privateKey"))
                        .connectTimeout(Duration.ofMillis(Math.min(settings.timeout(), 20_000))).readTimeout(Duration.ofMillis(settings.timeout()));
                if (settings.audience() != null) {
                    oauth2.audience(settings.audience());
                }
                if (settings.scope() != null) {
                    oauth2.scope(settings.scope());
                }
                if (settings.trustCerts() != null) {
                    oauth2.trustCertsFilePath(settings.trustCerts());
                }
                yield oauth2.build();
            }
            case PulsarSettings.TLS -> AuthenticationFactory.TLS(String.valueOf(settings.certificate()), String.valueOf(settings.key()));
            default -> null;
        };
    }

    /**
     * Names why something failed.
     *
     * @param failed what was thrown
     * @return the failure to report
     */
    static LookupException failure(Throwable failed)
    {
        if (failed instanceof LookupException known) {
            return known;
        }
        String text = message(failed);
        return new LookupException(reason(failed), text, failed);
    }

    private static LookupException.Reason reason(Throwable failed)
    {
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof PulsarClientException.AuthenticationException || cause instanceof PulsarClientException.AuthorizationException) {
                return cause instanceof PulsarClientException.AuthenticationException ? LookupException.Reason.AUTHENTICATION_FAILED
                        : LookupException.Reason.ACCESS_DENIED;
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                    || cause instanceof SocketTimeoutException || cause instanceof TimeoutException
                    || cause instanceof PulsarAdminException.TimeoutException) {
                return LookupException.Reason.UNREACHABLE;
            }
            if (cause instanceof PulsarAdminException admin && admin.getStatusCode() > 0) {
                return status(admin.getStatusCode(), message(admin));
            }
        }
        return LookupException.Reason.FAILED;
    }

    /**
     * Names a refusal by its HTTP status: Pulsar answers both a refused sign-in and a refused request with 401 or 403, and
     * its message tells them apart.
     */
    private static LookupException.Reason status(int status, String message)
    {
        String text = message.toLowerCase(Locale.ROOT);
        boolean signIn = text.contains("authentication") || text.contains("authenticate") || text.contains("unauthenticated");
        return switch (status) {
            case 401, 403 -> signIn ? LookupException.Reason.AUTHENTICATION_FAILED : LookupException.Reason.ACCESS_DENIED;
            case 404 -> LookupException.Reason.NOT_FOUND;
            // A proxy or load balancer in front of the brokers answers for them when it cannot reach one.
            case 502, 503, 504 -> LookupException.Reason.UNREACHABLE;
            default -> LookupException.Reason.FAILED;
        };
    }

    /**
     * Returns what a failure says: its message, or Pulsar's answer, or else its cause's.
     *
     * @param failed what was thrown
     * @return the text
     */
    static String message(Throwable failed)
    {
        String message = failed instanceof PulsarAdminException admin && (failed.getMessage() == null || failed.getMessage().isBlank())
                ? admin.getHttpError() : failed.getMessage();
        Throwable cause = failed.getCause();
        if ((message == null || message.isBlank()) && cause != null) {
            return message(cause);
        }
        return message == null || message.isBlank() ? failed.getClass().getSimpleName() : message.strip();
    }
}
