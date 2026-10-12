// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * One session with an ensemble for one piece of work, through ZooKeeper's own client: it connects to the servers of the
 * connection string, signs in as the service says (digest, or Kerberos through a JAAS section of its own), waits until
 * the session can be used, runs the work and closes the session again.
 */
final class ZooKeeperClient
{
    /** The settings the client takes from a service, under the service type's field names. */
    private final String connect;
    private final String authentication;
    private final @Nullable String username;
    private final @Nullable String password;
    private final @Nullable String keytab;
    private final @Nullable String serverPrincipal;
    private final String serviceName;
    private final boolean secure;
    private final ServiceConfig config;
    private final int sessionTimeout;
    private final int connectTimeout;

    /**
     * Reads a service's settings.
     *
     * @param config the service's configuration, checked by {@link ZooKeeperProvider#validateConfig}
     */
    ZooKeeperClient(ServiceConfig config)
    {
        this.config = requireNonNull(config, "config");
        this.connect = config.require(ZooKeeperProvider.CONNECT).strip();
        String auth = config.get(ZooKeeperProvider.AUTHENTICATION);
        this.authentication = auth == null || auth.isBlank() ? ZooKeeperProvider.NONE : auth.strip().toLowerCase(Locale.ROOT);
        this.username = blankToNull(config.get(ZooKeeperProvider.USER));
        this.password = config.get(ZooKeeperProvider.PASSWORD);
        this.keytab = blankToNull(config.get(ZooKeeperProvider.KEYTAB));
        this.serverPrincipal = blankToNull(config.get(ZooKeeperProvider.SERVER_PRINCIPAL));
        String service = blankToNull(config.get(ZooKeeperProvider.SERVICE_NAME));
        this.serviceName = service == null ? ZKClientConfig.ZK_SASL_CLIENT_USERNAME_DEFAULT : service;
        this.secure = config.getBoolean(ZooKeeperProvider.SECURE, false);
        this.sessionTimeout = (int) config.getLong(ZooKeeperProvider.SESSION_TIMEOUT, 15_000);
        this.connectTimeout = (int) config.getLong(ZooKeeperProvider.CONNECT_TIMEOUT, 10_000);
    }

    /** Work done in a session. */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param zookeeper the session, connected and signed in
         * @return the result
         * @throws KeeperException when the ensemble refuses an operation
         * @throws InterruptedException when the thread is interrupted
         * @throws IOException when the work fails otherwise
         */
        T run(ZooKeeper zookeeper) throws KeeperException, InterruptedException, IOException;
    }

    /**
     * Connects, runs the work and closes the session.
     *
     * @param work what to do
     * @param <T> the result's type
     * @return what the work returned
     * @throws IOException when no server answers in time, signing in fails or the work fails
     * @throws KeeperException when the ensemble refuses an operation
     */
    // Restoring the interrupt is the one use of threads here: ZooKeeper's own client runs its threads.
    @SuppressWarnings({"PMD.DoNotUseThreads", "try"})
    <T> T run(Work<T> work) throws IOException, KeeperException
    {
        boolean kerberos = ZooKeeperProvider.KERBEROS.equals(authentication);
        ZKClientConfig settings = settings(kerberos);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<Watcher.Event.@Nullable KeeperState> refused = new AtomicReference<>();
        Watcher watcher = (WatchedEvent event) -> {
            switch (event.getState()) {
                // With Kerberos the session is usable once SASL is done, which ZooKeeper reports separately.
                case SyncConnected -> {
                    if (!kerberos) {
                        ready.countDown();
                    }
                }
                case SaslAuthenticated -> ready.countDown();
                case AuthFailed, Expired -> {
                    refused.set(event.getState());
                    ready.countDown();
                }
                default -> {
                    // Disconnected and the like: the client retries the other servers on its own.
                }
            }
        };
        try (ZooKeeper zookeeper = new ZooKeeper(connect, sessionTimeout, watcher, settings)) {
            if (!ready.await(connectTimeout, TimeUnit.MILLISECONDS)) {
                throw new ConnectException("no ZooKeeper server of " + connect + " answered within " + connectTimeout + " ms");
            }
            Watcher.Event.@Nullable KeeperState failure = refused.get();
            if (failure == Watcher.Event.KeeperState.AuthFailed) {
                throw new ZooKeeperLoginException("the ensemble refused the sign-in of " + username
                        + "; check the principal, its keytab and the server principal");
            }
            if (failure != null) {
                throw new ConnectException("the ZooKeeper session ended before it could be used: " + failure);
            }
            if (ZooKeeperProvider.DIGEST.equals(authentication) && username != null) {
                zookeeper.addAuthInfo("digest", (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
            }
            return work.run(zookeeper);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            InterruptedIOException stopped = new InterruptedIOException("interrupted while talking to " + connect);
            stopped.initCause(interrupted);
            throw stopped;
        }
    }

    private ZKClientConfig settings(boolean kerberos) throws IOException
    {
        ZKClientConfig settings = new ZKClientConfig();
        if (kerberos) {
            String principal = requireNonNull(username, "username");
            String file = requireNonNull(keytab, "keytab");
            if (!Files.isReadable(Path.of(file))) {
                throw new ZooKeeperLoginException("the keytab " + file + " cannot be read by the GrantForge server");
            }
            settings.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "true");
            settings.setProperty(ZKClientConfig.LOGIN_CONTEXT_NAME_KEY, ZooKeeperJaas.section(principal, file));
            settings.setProperty(ZKClientConfig.ZK_SASL_CLIENT_USERNAME, serviceName);
            if (serverPrincipal != null) {
                settings.setProperty(ZKClientConfig.ZOOKEEPER_SERVER_PRINCIPAL, serverPrincipal);
            }
        }
        else {
            // Without this the client tries the process's default JAAS section, which belongs to someone else.
            settings.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "false");
        }
        if (secure) {
            settings.setProperty(ZKClientConfig.SECURE_CLIENT, "true");
            settings.setProperty(ZKClientConfig.ZOOKEEPER_CLIENT_CNXN_SOCKET, "org.apache.zookeeper.ClientCnxnSocketNetty");
            copy(settings, ZooKeeperProvider.TRUSTSTORE, "zookeeper.ssl.trustStore.location");
            copy(settings, ZooKeeperProvider.TRUSTSTORE_PASSWORD, "zookeeper.ssl.trustStore.password");
            copy(settings, ZooKeeperProvider.TRUSTSTORE_TYPE, "zookeeper.ssl.trustStore.type");
            copy(settings, ZooKeeperProvider.KEYSTORE, "zookeeper.ssl.keyStore.location");
            copy(settings, ZooKeeperProvider.KEYSTORE_PASSWORD, "zookeeper.ssl.keyStore.password");
            copy(settings, ZooKeeperProvider.KEYSTORE_TYPE, "zookeeper.ssl.keyStore.type");
        }
        return settings;
    }

    private void copy(ZKClientConfig settings, String field, String property)
    {
        String value = blankToNull(config.get(field));
        if (value != null) {
            settings.setProperty(property, value);
        }
    }

    /**
     * Reads the children of a znode, sorted by name, unless there are more than a service lets a lookup read.
     *
     * @param zookeeper the session
     * @param path the znode
     * @param limit the most children read
     * @return the children's names
     * @throws KeeperException when the znode does not exist or may not be read
     * @throws InterruptedException when the thread is interrupted
     * @throws TooManyChildrenException when there are more than {@code limit}
     */
    static List<String> children(ZooKeeper zookeeper, String path, int limit) throws KeeperException, InterruptedException, TooManyChildrenException
    {
        List<String> names = zookeeper.getChildren(path, false);
        if (names.size() > limit) {
            throw new TooManyChildrenException(path, names.size(), limit);
        }
        return names.stream().sorted().toList();
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
