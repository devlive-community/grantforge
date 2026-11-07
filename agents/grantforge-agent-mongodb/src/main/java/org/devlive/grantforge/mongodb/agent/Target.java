// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * What a privilege of a role is on, in MongoDB's terms: the deployment as a whole, or a collection. An empty collection
 * is every collection of the database but its system collections, and an empty database every database.
 *
 * @param database the database, empty for every one; {@code null} for the deployment as a whole
 * @param collection the collection, empty for every one; {@code null} for the deployment as a whole
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
record Target(@Nullable String database, @Nullable String collection)
{
    /** The deployment as a whole. */
    static final Target DEPLOYMENT = new Target(null, null);

    /** The order privileges are compared and written in. */
    static final Comparator<Target> ORDER = Comparator.comparing((Target target) -> target.database() == null ? "" : target.database())
            .thenComparing(target -> target.collection() == null ? "" : target.collection());

    /**
     * Names every collection of a database.
     *
     * @param database the database
     * @return the target
     */
    static Target database(String database)
    {
        return new Target(database, "");
    }

    /**
     * Names a collection.
     *
     * @param database its database
     * @param collection the collection
     * @return the target
     */
    static Target collection(String database, String collection)
    {
        return new Target(database, collection);
    }

    /**
     * Reads a resource of a role's privilege.
     *
     * @param resource the resource
     * @return the target, or {@code null} for what the agent does not grant, such as {@code anyResource}
     */
    static @Nullable Target of(Document resource)
    {
        if (Boolean.TRUE.equals(resource.getBoolean("cluster"))) {
            return DEPLOYMENT;
        }
        String database = resource.getString("db");
        String collection = resource.getString("collection");
        return database == null || collection == null ? null : new Target(database, collection);
    }

    /**
     * Writes the resource of a role's privilege.
     *
     * @return the resource
     */
    Document document()
    {
        if (database == null || collection == null) {
            return new Document("cluster", true);
        }
        return new Document("db", database).append("collection", collection);
    }

    /**
     * Returns whether a database and collection are within the target.
     *
     * @param databaseName the database
     * @param collectionName the collection, which is no system collection
     * @return whether they are
     */
    boolean covers(String databaseName, String collectionName)
    {
        if (database == null || collection == null) {
            return false;
        }
        return (database.isEmpty() || database.equals(databaseName)) && (collection.isEmpty() || collection.equals(collectionName));
    }

    /**
     * Returns whether every collection of a database is within the target, so that the database as such is too.
     *
     * @param databaseName the database
     * @return whether it is
     */
    boolean coversAll(String databaseName)
    {
        return database != null && collection != null && collection.isEmpty() && (database.isEmpty() || database.equals(databaseName));
    }

    /**
     * Says what the target is, for messages.
     *
     * @return such as {@code sales.orders}
     */
    String label()
    {
        if (database == null || collection == null) {
            return "the deployment";
        }
        return (database.isEmpty() ? "*" : database) + "." + (collection.isEmpty() ? "*" : collection);
    }
}
