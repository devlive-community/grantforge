// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.LookupException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisAccessControlException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Lists the nodes, keys or channels that start with what was typed. Keys are scanned on each primary, a few rounds at
 * most, so that a lookup stays quick however many keys there are; channels are those that have subscribers now. Redis
 * lists every key to a user who may scan, so the lookup user should be one that may read what policies are written for.
 *
 * @param resource the level looked up
 * @param typed what was typed, stripped
 * @param limit the most values wanted
 */
record RedisLookup(String resource, String typed, int limit)
{
    /** The most rounds of {@code SCAN} a lookup takes of one node. */
    static final int SCAN_ROUNDS = 20;
    /** How many keys one round of {@code SCAN} asks for. */
    static final int SCAN_COUNT = 1000;

    /**
     * Builds a lookup.
     *
     * @param resource the level
     * @param typed what the user typed
     * @param limit the most values wanted
     * @return the lookup
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static RedisLookup of(String resource, String typed, int limit)
    {
        if (!List.of(RedisProvider.NODE, RedisProvider.KEY, RedisProvider.CHANNEL).contains(resource)) {
            throw new IllegalArgumentException("Redis services have no resource " + resource);
        }
        return new RedisLookup(resource, typed.strip(), Math.max(1, limit));
    }

    /**
     * Runs the lookup.
     *
     * @param client the deployment
     * @return the names, sorted, at most {@code limit}
     * @throws LookupException when no node can be reached, the sign-in is refused or a command fails
     */
    List<String> run(RedisClient client)
    {
        try {
            Topology topology = client.topology();
            SortedSet<String> found = new TreeSet<>();
            switch (resource) {
                case RedisProvider.NODE -> topology.addresses().stream().filter(address -> address.startsWith(typed)).forEach(found::add);
                case RedisProvider.KEY -> {
                    for (String primary : topology.primaries()) {
                        found.addAll(client.on(primary, this::keys));
                    }
                }
                default -> {
                    for (String address : topology.addresses()) {
                        found.addAll(client.on(address, node -> node.pubsubChannels(escape(typed) + "*")));
                    }
                }
            }
            return found.stream().limit(limit).toList();
        }
        catch (JedisException failed) {
            throw failure(failed);
        }
    }

    private SortedSet<String> keys(Jedis node)
    {
        SortedSet<String> keys = new TreeSet<>();
        ScanParams match = new ScanParams().match(escape(typed) + "*").count(SCAN_COUNT);
        String cursor = ScanParams.SCAN_POINTER_START;
        for (int round = 0; round < SCAN_ROUNDS && keys.size() < limit; round++) {
            ScanResult<String> page = node.scan(cursor, match);
            keys.addAll(page.getResult());
            cursor = page.getCursor();
            if (page.isCompleteIteration()) {
                break;
            }
        }
        return keys;
    }

    /**
     * Escapes what Redis's patterns read as wildcards, so that typed text matches itself.
     *
     * @param text the text
     * @return it with {@code *}, {@code ?}, {@code [}, {@code ]} and backslashes escaped
     */
    static String escape(String text)
    {
        return text.replaceAll("([*?\\[\\]\\\\])", "\\\\$1");
    }

    /**
     * Says why Redis failed, as a lookup failure of the matching reason.
     *
     * @param failed what Jedis threw
     * @return the failure
     */
    static LookupException failure(JedisException failed)
    {
        String message = String.valueOf(failed.getMessage());
        // Jedis throws refused sign-ins and missing permissions alike; Redis's error codes tell them apart.
        if (message.startsWith("NOPERM")) {
            return new LookupException(LookupException.Reason.ACCESS_DENIED, "Redis refused the lookup user: " + message, failed);
        }
        if (failed instanceof JedisAccessControlException || message.startsWith("WRONGPASS") || message.startsWith("NOAUTH")) {
            return new LookupException(LookupException.Reason.AUTHENTICATION_FAILED, "Redis refused the sign-in: " + message, failed);
        }
        if (failed instanceof JedisConnectionException) {
            return new LookupException(LookupException.Reason.UNREACHABLE, "no Redis node can be reached: " + message, failed);
        }
        return new LookupException(LookupException.Reason.FAILED, "Redis failed the lookup: " + message, failed);
    }
}
