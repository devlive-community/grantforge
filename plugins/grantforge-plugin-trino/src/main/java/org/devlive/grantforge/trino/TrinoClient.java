// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import io.trino.jdbc.TrinoDriver;
import org.devlive.grantforge.plugin.api.LookupException;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * One connection to a Trino coordinator for one piece of work, through Trino's own JDBC driver. With Kerberos the
 * principal signs in first, from its keytab or with its password, and the driver is handed the credential of that
 * sign-in, so nothing of the GrantForge server's own Kerberos settings is used. Failures are named by their kind: a
 * refused sign-in, missing permissions, or a coordinator that cannot be reached.
 */
final class TrinoClient
{
    /** The driver's property that takes a delegated Kerberos credential. */
    static final String DELEGATION = "KerberosConstrainedDelegation";
    /** The driver's switch that makes it take {@link #DELEGATION} instead of a login of its own. */
    static final String DELEGATING = "KerberosDelegation";

    private static final String KERBEROS_V5 = "1.2.840.113554.1.2.2";

    /** Opens a connection; the driver itself, or a stand-in in tests. */
    @FunctionalInterface
    interface Connector
    {
        /**
         * Connects.
         *
         * @param url the URL
         * @param properties the driver's properties
         * @return the connection, or {@code null} when the driver does not take the URL
         * @throws SQLException when the coordinator cannot be reached or refuses the sign-in
         */
        @Nullable Connection connect(String url, Properties properties) throws SQLException;
    }

    /** Signs in to Kerberos; the JDK's login module, or a stand-in in tests. */
    @FunctionalInterface
    interface Login
    {
        /**
         * Signs in.
         *
         * @param principal the principal
         * @param keytab its keytab, or {@code null}
         * @param password its password, used without a keytab
         * @param kdcs the KDCs, or {@code null}
         * @return the signed-in subject
         * @throws LoginException when the KDC refuses
         * @throws IOException when the keytab cannot be read
         */
        Subject login(String principal, @Nullable String keytab, @Nullable String password, @Nullable String kdcs)
                throws LoginException, IOException;
    }

    /**
     * Work done on a connection.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param connection the connection, signed in
         * @return the result
         * @throws SQLException when Trino fails the work
         */
        T run(Connection connection) throws SQLException;
    }

    private final TrinoSettings settings;
    private final Connector connector;
    private final Login login;

    TrinoClient(TrinoSettings settings)
    {
        this(settings, (url, properties) -> new TrinoDriver().connect(url, properties), KerberosLogin::login);
    }

    TrinoClient(TrinoSettings settings, Connector connector, Login login)
    {
        this.settings = requireNonNull(settings, "settings");
        this.connector = requireNonNull(connector, "connector");
        this.login = requireNonNull(login, "login");
    }

    /**
     * Connects, runs the work and closes the connection.
     *
     * @param work what to do
     * @param <T> the result's type
     * @return what the work returned
     * @throws LookupException naming why it failed
     */
    <T> T run(Work<T> work)
    {
        Properties properties = settings.driver();
        try {
            if (settings.kerberos()) {
                Subject subject = login.login(settings.user(), settings.keytab(), settings.password(), settings.kdcs());
                properties.setProperty(DELEGATING, "true");
                properties.put(DELEGATION, Subjects.callAs(subject, TrinoClient::credential));
            }
            try (Connection connection = connector.connect(settings.url(), properties)) {
                if (connection == null) {
                    throw new SQLException("Trino's driver does not take " + settings.url());
                }
                return work.run(connection);
            }
        }
        catch (LookupException failed) {
            throw failed;
        }
        catch (Exception failed) {
            throw failure(failed);
        }
    }

    /** The Kerberos credential of the subject the call runs as. */
    private static GSSCredential credential() throws GSSException
    {
        return GSSManager.getInstance().createCredential(null, GSSCredential.DEFAULT_LIFETIME, new Oid(KERBEROS_V5), GSSCredential.INITIATE_ONLY);
    }

    /**
     * Names why something failed, from the first failure in the chain that tells.
     *
     * @param failed the failure
     * @return the lookup failure
     */
    static LookupException failure(Throwable failed)
    {
        String text = message(failed);
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
        String text = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
        if (cause instanceof LoginException || cause instanceof GSSException || text.startsWith("authentication failed")) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if (text.startsWith("access denied")) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        // A coordinator whose certificate cannot be verified is as good as unreachable: nothing may be asked of it.
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SocketTimeoutException || cause instanceof SSLException) {
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
