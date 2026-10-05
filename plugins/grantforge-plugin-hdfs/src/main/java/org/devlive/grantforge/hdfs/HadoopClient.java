// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

/**
 * Talks to a cluster with Hadoop's own client, as Apache Ranger's HDFS service does: the service's settings become a
 * Hadoop configuration, the lookup user signs in with simple authentication or Kerberos (keytab or password), and the
 * file system is used as that user, then closed. Each call builds its own file system, so services never share one.
 */
final class HadoopClient
{
    /** Hadoop keeps the Kerberos settings in static state; logins are done one at a time. */
    private static final ReentrantLock LOGIN = new ReentrantLock();

    /** Settings that make an unreachable cluster fail within the plugin call's time limit instead of retrying. */
    private static final Map<String, String> FAIL_FAST = Map.of(
            "ipc.client.connect.timeout", "5000",
            "ipc.client.connect.max.retries", "1",
            "ipc.client.connect.max.retries.on.timeouts", "1",
            "ipc.client.rpc-timeout.ms", "8000",
            "dfs.client.failover.max.attempts", "2",
            "dfs.client.retry.policy.enabled", "false",
            "dfs.webhdfs.socket.connect-timeout", "5s",
            "dfs.webhdfs.socket.read-timeout", "8s",
            "fs.hdfs.impl.disable.cache", "true",
            "fs.webhdfs.impl.disable.cache", "true");

    private final ServiceConfig config;

    HadoopClient(ServiceConfig config)
    {
        this.config = config;
    }

    /**
     * The Hadoop configuration of the service: fast-failing defaults, the service's named settings, then its additional
     * properties, which may override anything.
     */
    Configuration configuration()
    {
        Configuration hadoop = new Configuration();
        FAIL_FAST.forEach(hadoop::set);
        hadoop.set("fs.defaultFS", config.require(HdfsProvider.DEFAULT_FS));
        for (String name : HdfsProvider.HADOOP_SETTINGS) {
            String value = config.get(name);
            if (value != null && !value.isBlank()) {
                hadoop.set(name, value.strip());
            }
        }
        properties(config.get(HdfsProvider.EXTRA)).forEach(hadoop::set);
        return hadoop;
    }

    /**
     * Reads additional properties, one {@code key=value} per line; blank lines and lines starting with {@code #} are
     * skipped.
     *
     * @param text the lines, or {@code null}
     * @return the properties in their order
     * @throws IllegalArgumentException for a line that is not {@code key=value}
     */
    static Map<String, String> properties(@Nullable String text)
    {
        Map<String, String> properties = new LinkedHashMap<>();
        if (text == null) {
            return properties;
        }
        int number = 0;
        for (String line : text.split("\\R")) {
            number++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("line " + number + " is not key=value");
            }
            properties.put(trimmed.substring(0, equals).strip(), trimmed.substring(equals + 1).strip());
        }
        return properties;
    }

    /**
     * Runs an action on the file system as the lookup user.
     *
     * @param action the action
     * @param <T> what it returns
     * @return what it returned
     * @throws IOException if signing in, reaching the cluster or the action fails
     */
    // Restores the caller's interrupt when the call is interrupted; rethrows the action's own failure, not the wrapper
    // doAs puts around it.
    @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.PreserveStackTrace"})
    <T> T run(FileSystemAction<T> action) throws IOException
    {
        Configuration hadoop = configuration();
        UserGroupInformation user = login(hadoop);
        URI address = FileSystem.getDefaultUri(hadoop);
        try {
            return user.doAs((PrivilegedExceptionAction<T>) () -> {
                try (FileSystem files = FileSystem.newInstance(address, hadoop)) {
                    return action.apply(files);
                }
            });
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reaching " + address, interrupted);
        }
        catch (UndeclaredThrowableException wrapped) {
            if (wrapped.getCause() instanceof IOException failure) {
                throw failure;
            }
            throw new IOException(String.valueOf(wrapped.getCause()), wrapped);
        }
    }

    private UserGroupInformation login(Configuration hadoop) throws IOException
    {
        String user = config.require(HdfsProvider.USER).strip();
        if (!HdfsProvider.KERBEROS.equals(config.get(HdfsProvider.AUTHENTICATION))) {
            LOGIN.lock();
            try {
                UserGroupInformation.setConfiguration(hadoop);
                return UserGroupInformation.createRemoteUser(user);
            }
            finally {
                LOGIN.unlock();
            }
        }
        String keytab = config.get(HdfsProvider.KEYTAB);
        String password = config.get(HdfsProvider.PASSWORD);
        String keytabPath = keytab == null || keytab.isBlank() ? null : keytab.strip();
        boolean withKeytab = keytabPath != null;
        if (!withKeytab && (password == null || password.isEmpty())) {
            throw new IOException("Kerberos needs the lookup user's password or a keytab");
        }
        LOGIN.lock();
        try {
            try {
                UserGroupInformation.setConfiguration(hadoop);
            }
            catch (IllegalArgumentException unconfigured) {
                throw new IOException("Kerberos is not set up on the GrantForge server (krb5.conf, or java.security.krb5.realm and"
                        + " .kdc): " + unconfigured.getMessage(), unconfigured);
            }
            UserGroupInformation signedIn = keytabPath != null ? UserGroupInformation.loginUserFromKeytabAndReturnUGI(user, keytabPath)
                    : UserGroupInformation.getUGIFromSubject(passwordLogin(user, requireNonNull(password, "password")));
            // A local file system asks for no credentials; a cluster would refuse later, so tell now.
            if (!signedIn.hasKerberosCredentials() || !user.equals(signedIn.getUserName())) {
                throw new IOException("Kerberos gave no credentials of " + user + (keytabPath == null ? "" : "; is it in " + keytabPath + "?"));
            }
            return signedIn;
        }
        finally {
            LOGIN.unlock();
        }
    }

    /** Signs a principal in with its password through the JDK's Kerberos login module. */
    private static Subject passwordLogin(String principal, String password) throws IOException
    {
        Map<String, String> options = Map.of("useTicketCache", "false", "refreshKrb5Config", "true", "storeKey", "true",
                "doNotPrompt", "false", "principal", principal);
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
            LoginContext context = new LoginContext("grantforge-hdfs", subject, callbacks -> answer(callbacks, principal, password), jaas);
            context.login();
            return subject;
        }
        catch (LoginException refused) {
            throw new IOException("Kerberos refused " + principal + ": " + refused.getMessage(), refused);
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
     * Something done with the file system.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface FileSystemAction<T>
    {
        /**
         * Does it.
         *
         * @param files the file system
         * @return the result
         * @throws IOException if the file system fails
         */
        T apply(FileSystem files) throws IOException;
    }
}
