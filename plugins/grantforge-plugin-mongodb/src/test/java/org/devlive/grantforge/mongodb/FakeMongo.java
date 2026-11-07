// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A driver client that answers from tables: databases and their collections, and commands by name. */
final class FakeMongo
{
    private FakeMongo()
    {
    }

    /**
     * Makes the client.
     *
     * @param collections the databases and their collections
     * @param answers the answers of commands, by the command's first key
     * @return the client
     */
    static MongoClient client(Map<String, List<String>> collections, Map<String, Document> answers)
    {
        MongoClient client = mock(MongoClient.class);
        MongoIterable<String> names = names(List.copyOf(new java.util.TreeSet<>(collections.keySet())));
        when(client.listDatabaseNames()).thenReturn(names);
        when(client.getDatabase(anyString())).thenAnswer(call -> database(collections.getOrDefault(call.getArgument(0, String.class), List.of()), answers));
        return client;
    }

    private static MongoDatabase database(List<String> collections, Map<String, Document> answers)
    {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable listed = mock(ListCollectionNamesIterable.class);
        when(listed.authorizedCollections(anyBoolean())).thenReturn(listed);
        when(listed.iterator()).thenAnswer(call -> cursor(collections));
        when(database.listCollectionNames()).thenReturn(listed);
        when(database.runCommand(any(Bson.class))).thenAnswer(call -> answers.getOrDefault(call.getArgument(0, Document.class).keySet().iterator().next(), new Document()));
        return database;
    }

    @SuppressWarnings("unchecked")
    private static MongoIterable<String> names(List<String> values)
    {
        MongoIterable<String> names = mock(MongoIterable.class);
        when(names.iterator()).thenAnswer(call -> cursor(values));
        return names;
    }

    @SuppressWarnings("unchecked")
    private static MongoCursor<String> cursor(List<String> values)
    {
        Iterator<String> iterator = values.iterator();
        MongoCursor<String> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenAnswer(call -> iterator.hasNext());
        when(cursor.next()).thenAnswer(call -> iterator.next());
        return cursor;
    }
}
