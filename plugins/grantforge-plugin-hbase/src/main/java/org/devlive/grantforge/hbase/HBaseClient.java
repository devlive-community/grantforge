// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.exceptions.ConnectionClosedException;
import org.apache.hadoop.hbase.exceptions.TimeoutIOException;
import org.apache.hadoop.hbase.ipc.CallTimeoutException;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

/**
 * Runs work against an HBase cluster through a connection opened for it and closed after it, as the lookup user. Hadoop's
 * sign-in keeps its settings in static state, so one call runs at a time; Kerberos sign-ins are kept, a keytab's renewed
 * from it and a password's used while its ticket lasts. HBase loads classes through the thread's context class loader, so
 * the work runs with the plugin's. Failures are named by their kind: a refused sign-in, missing permissions, or a cluster
 * that cannot be reached.
 */
final class HBaseClient
{
    private static final ReentrantLock LOGIN = new ReentrantLock();
    private static final Map<LoginKey, CachedLogin> LOGINS = new ConcurrentHashMap<>();
    private static final Duration MIN_TICKET_LEFT = Duration.ofMinutes(1);

    /** Opens a connection. */
    @FunctionalInterface
    interface Opener
    {
        /**
         * Opens a connection.
         *
         * @param configuration the client configuration
         * @param user who it acts as
         * @return the connection
         * @throws IOException if the cluster cannot be reached
         */
        Connection open(Configuration configuration, User user) throws IOException;
    }

    /**
     * Work with the cluster's administration API.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param admin the administration API
         * @return the result
         * @throws IOException when HBase fails the request
         */
        T run(Admin admin) throws IOException;
    }

    private final HBaseSettings settings;
    private final Opener opener;

    HBaseClient(HBaseSettings settings)
    {
        this(settings, ConnectionFactory::createConnection);
    }

    HBaseClient(HBaseSettings settings, Opener opener)
    {
        this.settings = requireNonNull(settings, "settings");
        this.opener = requireNonNull(opener, "opener");
    }

    /**
     * Signs in, opens a connection, runs the work and closes the connection.
     *
     * @param work the work
     * @param <T> what it returns
     * @return the result
     * @throws LookupException naming why it failed
     */
    // The work runs with the plugin's class loader as the context one; Hadoop's sign-in is serialized. doAs passes on what
    // the work throws, an IOException or a runtime exception, as it is.
    @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.UseProperClassLoader"})
    <T> T run(Work<T> work)
    {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(HBaseClient.class.getClassLoader());
        boolean locked = false;
        try {
            LOGIN.lockInterruptibly();
            locked = true;
            Configuration configuration = settings.client();
            UserGroupInformation user = login(configuration);
            return user.doAs((PrivilegedExceptionAction<T>) () -> {
                try (Connection connection = opener.open(configuration, User.create(user)); Admin admin = connection.getAdmin()) {
                    return work.run(admin);
                }
            });
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LookupException(LookupException.Reason.FAILED, "interrupted while waiting for HBase", interrupted);
        }
        catch (IOException | RuntimeException failed) {
            throw failure(failed);
        }
        finally {
            if (locked) {
                LOGIN.unlock();
            }
            thread.setContextClassLoader(previous);
        }
    }

    private UserGroupInformation login(Configuration configuration) throws IOException
    {
        if (!settings.kerberos()) {
            UserGroupInformation.setConfiguration(configuration);
            return UserGroupInformation.createRemoteUser(settings.user());
        }
        String principal = settings.user();
        String keytab = settings.keytab();
        String password = settings.password();
        String kdcs = settings.kdcs();
        if (kdcs != null) {
            KerberosRealms.declare(KerberosRealms.realm(principal), KerberosRealms.kdcs(kdcs));
        }
        try {
            UserGroupInformation.setConfiguration(configuration);
        }
        catch (IllegalArgumentException unconfigured) {
            throw new HBaseLoginException("Kerberos is not set up on the GrantForge server (krb5.conf, java.security.krb5.realm and .kdc,"
                    + " or the service's KDCs): " + unconfigured.getMessage(), unconfigured);
        }
        LoginKey key = LoginKey.of(principal, keytab, password);
        CachedLogin cached = LOGINS.get(key);
        if (cached != null && cached.usable(Instant.now())) {
            return cached.user();
        }
        LOGINS.remove(key);
        if (keytab != null && !new File(keytab).canRead()) {
            throw new HBaseLoginException("the keytab " + keytab + " cannot be read by the GrantForge server", null);
        }
        Subject subject = keytab == null ? passwordLogin(principal, requireNonNull(password, "password")) : null;
        UserGroupInformation signedIn;
        try {
            signedIn = subject == null ? UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytab)
                    : UserGroupInformation.getUGIFromSubject(subject);
        }
        catch (IOException refused) {
            throw new HBaseLoginException("Kerberos refused " + principal + ": " + refused.getMessage(), refused);
        }
        if (!signedIn.hasKerberosCredentials()) {
            throw new HBaseLoginException("Kerberos gave no credentials of " + principal + (keytab == null ? "" : "; is it in " + keytab + "?"),
                    null);
        }
        LOGINS.put(key, new CachedLogin(signedIn, subject));
        return signedIn;
    }

    /** Forgets every kept sign-in, as when the server's Kerberos setup changes; for tests. */
    static void forgetLogins()
    {
        LOGIN.lock();
        try {
            LOGINS.clear();
        }
        finally {
            LOGIN.unlock();
        }
    }

    /** Signs a principal in with its password through the JDK's Kerberos login module. */
    private static Subject passwordLogin(String principal, String password) throws HBaseLoginException
    {
        Map<String, String> options = Map.of("useTicketCache", "false", "refreshKrb5Config", "true", "storeKey", "true", "doNotPrompt", "false",
                "principal", principal);
        javax.security.auth.login.Configuration jaas = new javax.security.auth.login.Configuration()
        {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name)
            {
                return new AppConfigurationEntry[] {new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)};
            }
        };
        Subject subject = new Subject();
        try {
            new LoginContext("grantforge-hbase", subject, callbacks -> answer(callbacks, principal, password), jaas).login();
            return subject;
        }
        catch (LoginException refused) {
            throw new HBaseLoginException("Kerberos refused " + principal + ": " + refused.getMessage(), refused);
        }
    }

    private static void answer(Callback[] callbacks, String principal, String password) throws UnsupportedCallbackException
    {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback name) {
                name.setName(principal);
            }
            else if (callback instanceof PasswordCallback secret) {
                secret.setPassword(password.toCharArray());
            }
            else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    /**
     * What tells sign-ins apart: the principal and its credentials. A keytab counts with its modification time and size,
     * so a rotated keytab signs in again; a password only as its digest, so it is never kept as a key.
     */
    private record LoginKey(String principal, @Nullable String keytab, long keytabModified, long keytabSize, @Nullable String password)
    {
        static LoginKey of(String principal, @Nullable String keytab, @Nullable String password)
        {
            if (keytab != null) {
                File file = new File(keytab);
                return new LoginKey(principal, keytab, file.lastModified(), file.length(), null);
            }
            return new LoginKey(principal, null, 0, 0, password == null ? null : digest(password));
        }

        private static String digest(String password)
        {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8)));
            }
            catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is not available", impossible);
            }
        }
    }

    /**
     * A kept sign-in. A keytab's is renewed by Hadoop from the keytab when its ticket nears its end; a password's cannot
     * be, so it is used only while its ticket has a fifth of its life, and at least a minute, left.
     *
     * @param user the signed-in user
     * @param passwordSubject the subject of a password sign-in, holding its ticket; {@code null} for a keytab
     */
    record CachedLogin(UserGroupInformation user, @Nullable Subject passwordSubject)
    {
        boolean usable(Instant now)
        {
            if (passwordSubject == null) {
                try {
                    user.checkTGTAndReloginFromKeytab();
                    return true;
                }
                catch (IOException expired) {
                    return false;
                }
            }
            return passwordSubject.getPrivateCredentials(KerberosTicket.class).stream().anyMatch(ticket -> lasts(ticket, now));
        }

        static boolean lasts(KerberosTicket ticket, Instant now)
        {
            if (!ticket.getServer().getName().startsWith("krbtgt/") || ticket.getEndTime() == null || ticket.getStartTime() == null) {
                return false;
            }
            Instant start = ticket.getStartTime().toInstant();
            Instant end = ticket.getEndTime().toInstant();
            Duration margin = Duration.between(start, end).dividedBy(5);
            return now.plus(margin.compareTo(MIN_TICKET_LEFT) < 0 ? MIN_TICKET_LEFT : margin).isBefore(end);
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
        String text = message(failed);
        // A server that took the connection and closed it refused how the client signed in; it says no more.
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectionClosedException) {
                return new LookupException(LookupException.Reason.AUTHENTICATION_FAILED, "HBase closed the connection, as it does with clients"
                        + " that sign in other than it expects: check hbase.security.authentication and hbase.rpc.protection (" + text + ")", failed);
            }
        }
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
        if (cause instanceof HBaseLoginException || cause instanceof LoginException || cause instanceof SaslException) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if (cause instanceof AccessDeniedException) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SocketTimeoutException || cause instanceof TimeoutException || cause instanceof TimeoutIOException
                || cause instanceof CallTimeoutException || cause instanceof MasterNotRunningException) {
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
