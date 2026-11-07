// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lists the deployment, the databases or the collections that start with what was typed, within the databases a policy
 * already names. MongoDB lists the databases and collections a user has any privilege on, so a lookup shows what the
 * lookup user may see. Policy values of the database are wildcards; the names they match are looked in, at most
 * {@value #MAX_PARENTS} of them.
 *
 * @param resource the level looked up
 * @param typed what was typed, stripped
 * @param databases the database wildcards the policy names, or none for every database
 * @param limit the most values wanted
 */
record MongoDbLookup(String resource, String typed, List<String> databases, int limit)
{
    /** The most names of a level above that a lookup looks in. */
    static final int MAX_PARENTS = 100;

    /** The databases MongoDB keeps for itself; policies do not decide them. */
    static final Set<String> SYSTEM_DATABASES = Set.of("admin", "config", "local");

    /** What the names of collections MongoDB keeps for itself start with. */
    static final String SYSTEM_COLLECTIONS = "system.";

    /** MongoDB's error codes for a missing privilege and a refused sign-in. */
    private static final int UNAUTHORIZED = 13;
    private static final int AUTHENTICATION_FAILED = 18;

    /** Copies the wildcards. */
    MongoDbLookup
    {
        databases = List.copyOf(databases);
    }

    /**
     * Builds a lookup.
     *
     * @param resource the level
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most values wanted
     * @return the lookup
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static MongoDbLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!List.of(MongoDbProvider.DEPLOYMENT, MongoDbProvider.DATABASE, MongoDbProvider.COLLECTION).contains(resource)) {
            throw new IllegalArgumentException("MongoDB services have no resource " + resource);
        }
        List<String> databases = context.get(MongoDbProvider.DATABASE);
        return new MongoDbLookup(resource, typed.strip(), databases == null ? List.of() : databases.stream().map(String::strip).filter(value -> !value.isEmpty())
                .toList(), Math.max(1, limit));
    }

    /**
     * Runs the lookup.
     *
     * @param client the deployment
     * @return the names, sorted, at most {@code limit}
     * @throws LookupException when no server can be reached, the sign-in is refused or a command fails
     */
    List<String> run(MongoDbClient client)
    {
        try {
            return client.run(this::names);
        }
        catch (MongoException failed) {
            throw failure(failed);
        }
    }

    private List<String> names(MongoClient client)
    {
        SortedSet<String> found = new TreeSet<>();
        switch (resource) {
            case MongoDbProvider.DEPLOYMENT -> found.add(deployment(client.getDatabase("admin").runCommand(new Document("hello", 1))));
            case MongoDbProvider.DATABASE -> found.addAll(databases(client));
            default -> {
                for (String database : matching(databases(client), databases)) {
                    for (String collection : client.getDatabase(database).listCollectionNames().authorizedCollections(true)) {
                        if (!collection.startsWith(SYSTEM_COLLECTIONS)) {
                            found.add(collection);
                        }
                    }
                }
            }
        }
        return found.stream().filter(name -> name.startsWith(typed)).limit(limit).toList();
    }

    /**
     * Lists the databases the user sees, without MongoDB's own.
     *
     * @param client the client
     * @return their names
     */
    static List<String> databases(MongoClient client)
    {
        List<String> names = new ArrayList<>();
        for (String name : client.listDatabaseNames()) {
            if (!SYSTEM_DATABASES.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Names a deployment as policies name it.
     *
     * @param hello the answer of the {@code hello} command
     * @return the replica set's name, {@value MongoDbProvider#SHARDED} for a {@code mongos}, otherwise {@value MongoDbProvider#STANDALONE}
     */
    // A Document is what the driver answers with, not a choice among maps.
    @SuppressWarnings("PMD.LooseCoupling")
    static String deployment(Document hello)
    {
        String set = hello.getString("setName");
        if (set != null) {
            return set;
        }
        return "isdbgrid".equals(hello.getString("msg")) ? MongoDbProvider.SHARDED : MongoDbProvider.STANDALONE;
    }

    /**
     * Picks the names that match wildcards.
     *
     * @param names the names
     * @param wildcards the wildcards, or none for every name
     * @return the names that match, at most {@value #MAX_PARENTS}
     */
    static List<String> matching(List<String> names, List<String> wildcards)
    {
        if (wildcards.isEmpty()) {
            return names.stream().limit(MAX_PARENTS).toList();
        }
        List<Pattern> patterns = wildcards.stream().map(MongoDbLookup::pattern).toList();
        return names.stream().filter(name -> patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches())).limit(MAX_PARENTS).toList();
    }

    /**
     * Compiles a policy wildcard: {@code *} matches any text, {@code ?} one character and everything else itself.
     *
     * @param wildcard the wildcard
     * @return the pattern
     */
    static Pattern pattern(String wildcard)
    {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char character : wildcard.toCharArray()) {
            if (character == '*' || character == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(character == '*' ? ".*" : ".");
            }
            else {
                literal.append(character);
            }
        }
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return Pattern.compile(regex.toString());
    }

    /**
     * Says why MongoDB failed, as a lookup failure of the matching reason.
     *
     * @param failed what the driver threw
     * @return the failure
     */
    static LookupException failure(MongoException failed)
    {
        String message = String.valueOf(failed.getMessage());
        if (failed instanceof MongoSecurityException || failed.getCode() == AUTHENTICATION_FAILED) {
            Throwable cause = failed.getCause();
            return new LookupException(LookupException.Reason.AUTHENTICATION_FAILED, "MongoDB refused the sign-in: "
                    + (cause instanceof MongoCommandException command ? command.getErrorMessage() : message), failed);
        }
        if (failed.getCode() == UNAUTHORIZED) {
            return new LookupException(LookupException.Reason.ACCESS_DENIED, "MongoDB refused the lookup user: " + errorMessage(failed), failed);
        }
        if (failed instanceof MongoTimeoutException || failed instanceof MongoSocketException) {
            return new LookupException(LookupException.Reason.UNREACHABLE, "no MongoDB server can be reached: " + message, failed);
        }
        return new LookupException(LookupException.Reason.FAILED, "MongoDB failed the lookup: " + errorMessage(failed), failed);
    }

    /** What the server said, without the driver's wrapping of the whole command. */
    private static String errorMessage(MongoException failed)
    {
        @Nullable String message = failed instanceof MongoCommandException command ? command.getErrorMessage() : failed.getMessage();
        if (message == null) {
            return failed.getClass().getSimpleName();
        }
        // A refusal repeats the whole command; its first words say enough.
        int command = message.indexOf(" to execute command");
        return command < 0 ? message : message.substring(0, command);
    }
}
