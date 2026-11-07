// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The deployment's name, its databases and their collections, each database's collections read when first asked for.
 * MongoDB's own databases and system collections are not in it: policies do not decide them. Thread-safe: the reader of
 * the profiler asks for the databases too.
 */
final class Inventory
{
    /** The databases MongoDB keeps for itself. */
    static final Set<String> SYSTEM_DATABASES = Set.of("admin", "config", "local");

    /** What the names of collections MongoDB keeps for itself start with. */
    static final String SYSTEM_COLLECTIONS = "system.";

    /** What a sharded cluster is called in policies: it has no name of its own. */
    static final String SHARDED = "sharded";

    /** What a single server is called in policies. */
    static final String STANDALONE = "standalone";

    private final String deployment;
    private final List<String> databases;
    private final Function<String, List<String>> collections;
    private final Map<String, List<String>> collectionsRead = new ConcurrentHashMap<>();

    /**
     * Holds the inventory.
     *
     * @param deployment the deployment's name
     * @param databases the databases
     * @param collections reads a database's collections and views
     */
    Inventory(String deployment, List<String> databases, Function<String, List<String>> collections)
    {
        this.deployment = deployment;
        this.databases = databases.stream().filter(name -> !SYSTEM_DATABASES.contains(name)).sorted().toList();
        this.collections = collections;
    }

    /**
     * Returns the deployment's name.
     *
     * @return the replica set's name, {@value #SHARDED} or {@value #STANDALONE}
     */
    String deployment()
    {
        return deployment;
    }

    /**
     * Returns whether the deployment is a sharded cluster, whose {@code mongos} has no profiler.
     *
     * @return whether it is
     */
    boolean sharded()
    {
        return SHARDED.equals(deployment);
    }

    /**
     * Returns the databases.
     *
     * @return their names
     */
    List<String> databases()
    {
        return databases;
    }

    /**
     * Returns a database's collections and views.
     *
     * @param database the database
     * @return their names, sorted
     */
    List<String> collections(String database)
    {
        return collectionsRead.computeIfAbsent(database, name -> collections.apply(name).stream().filter(collection -> !collection.startsWith(SYSTEM_COLLECTIONS))
                .sorted().toList());
    }
}
