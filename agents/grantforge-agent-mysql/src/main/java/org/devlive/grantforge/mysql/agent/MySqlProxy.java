// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.mysql.agent.proxy.Gate;
import org.devlive.grantforge.mysql.agent.proxy.ProxyServer;
import org.devlive.grantforge.mysql.agent.sql.Policies;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A running proxy: the signed-policy agent that keeps the MySQL service's snapshot and ships access events, the catalog
 * of the server's schema and the listening socket.
 */
// A standalone server process: it runs its own threads to check statements.
@SuppressWarnings("PMD.DoNotUseThreads")
public final class MySqlProxy
        implements AutoCloseable
{
    /** The service type whose snapshots the proxy takes. */
    static final String SERVICE_TYPE = "mysql";

    private static final Logger LOG = Logger.getLogger(MySqlProxy.class.getName());

    private final GrantForgeAgent agent;
    private final JdbcCatalog catalog;
    private final ExecutorService checks;
    private final ProxyServer server;

    private MySqlProxy(GrantForgeAgent agent, JdbcCatalog catalog, ExecutorService checks, ProxyServer server)
    {
        this.agent = agent;
        this.catalog = catalog;
        this.checks = checks;
        this.server = server;
    }

    /**
     * Starts the proxy.
     *
     * @param settings its settings
     * @return the running proxy; {@link #close} it to stop
     * @throws SQLException when the metadata user cannot sign in to MySQL or lacks a privilege it needs
     * @throws InterruptedException when interrupted while binding
     */
    public static MySqlProxy start(ProxySettings settings) throws SQLException, InterruptedException
    {
        JdbcCatalog catalog = new JdbcCatalog(settings.metadataUrl(), settings.metadata(), settings.keep(), Clock.systemUTC());
        GrantForgeAgent agent = GrantForgeAgent.start(settings.agent(), Collections.emptyMap());
        AtomicInteger thread = new AtomicInteger();
        ExecutorService checks = Executors.newFixedThreadPool(settings.checkThreads(), task -> {
            Thread worker = new Thread(task, "grantforge-mysql-check-" + thread.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        AtomicBoolean warned = new AtomicBoolean();
        Gate gate = new Gate(catalog, () -> {
            Snapshot snapshot = agent.snapshot();
            if (snapshot != null && !SERVICE_TYPE.equals(snapshot.serviceType())) {
                if (warned.compareAndSet(false, true)) {
                    LOG.severe("The proxy's token is bound to a " + snapshot.serviceType() + " service, not a MySQL one; every statement is refused");
                }
                return Policies.of(null);
            }
            return Policies.of(snapshot);
        }, agent::record, checks);
        try {
            ProxyServer server = ProxyServer.start(settings.listen(), settings.backend(), gate);
            LOG.info("The GrantForge MySQL proxy listens on " + server.address() + " for " + settings.backend().host() + ":"
                    + settings.backend().port());
            return new MySqlProxy(agent, catalog, checks, server);
        }
        catch (InterruptedException | RuntimeException failed) {
            checks.shutdownNow();
            agent.close();
            catalog.close();
            throw failed;
        }
    }

    /**
     * Returns where the proxy listens.
     *
     * @return the address
     */
    public InetSocketAddress address()
    {
        return server.address();
    }

    /** Stops listening, closes the connections, flushes or spools the events and lets go of MySQL. */
    @Override
    public void close()
    {
        server.close();
        checks.shutdown();
        try {
            if (!checks.awaitTermination(5, TimeUnit.SECONDS)) {
                checks.shutdownNow();
            }
        }
        catch (InterruptedException interrupted) {
            checks.shutdownNow();
            Thread.currentThread().interrupt();
        }
        agent.close();
        catalog.close();
    }
}
