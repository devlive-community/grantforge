// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.mysql.agent.sql.Catalog;
import org.devlive.grantforge.mysql.agent.sql.Policies;
import org.devlive.grantforge.mysql.agent.sql.QueryGuard;
import org.devlive.grantforge.mysql.agent.sql.SqlAnalyzer;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * What every connection of the proxy checks its statements with.
 *
 * @param catalog the server's schema
 * @param policies the policies of the current snapshot, taken once per check
 * @param events where access events go
 * @param executor where checks run, off the connections' event loops, as they may ask the server for its schema
 */
public record Gate(Catalog catalog, Supplier<Policies> policies, Consumer<AccessEvent> events, Executor executor)
{
    /** Checks the parts. */
    public Gate
    {
        requireNonNull(catalog, "catalog");
        requireNonNull(policies, "policies");
        requireNonNull(events, "events");
        requireNonNull(executor, "executor");
    }

    SqlAnalyzer analyzer()
    {
        return new SqlAnalyzer(catalog);
    }

    QueryGuard guard()
    {
        return new QueryGuard(catalog);
    }
}
