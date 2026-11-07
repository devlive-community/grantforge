// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;

import java.time.Instant;
import java.util.List;

/**
 * The deployment as the agent sees it. Thread-safe: the sync and the reader of the profiler share it.
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
interface MongoDb
        extends AutoCloseable
{
    /**
     * Runs a command.
     *
     * @param database the database it runs in
     * @param command the command
     * @return the answer
     * @throws MongoFailure when the deployment refuses it or cannot be reached
     */
    Document command(String database, Document command);

    /**
     * Lists the databases.
     *
     * @return their names
     * @throws MongoFailure when the deployment refuses it or cannot be reached
     */
    List<String> databases();

    /**
     * Lists the collections and views of a database.
     *
     * @param database the database
     * @return their names
     * @throws MongoFailure when the deployment refuses it or cannot be reached
     */
    List<String> collections(String database);

    /**
     * Reads a database's profiler entries, oldest first.
     *
     * @param database the database
     * @param after the entries are later than this
     * @param until and not later than this
     * @param limit the most entries wanted
     * @return the entries
     * @throws MongoFailure when the deployment refuses it or cannot be reached
     */
    List<Document> profile(String database, Instant after, Instant until, int limit);

    /** Lets go of the deployment. */
    @Override
    void close();
}
