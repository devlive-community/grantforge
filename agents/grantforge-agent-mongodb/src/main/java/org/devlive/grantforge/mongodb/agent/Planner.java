// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Turns the policies of a snapshot into what each user's role holds, for the databases and collections the deployment
 * has at the moment.
 *
 * <p>An access type is granted on a database as a whole where a policy allows it there and nothing below could take it
 * away, so that collections created later have it at once; otherwise collection by collection. MongoDB's privilege to
 * drop a database is the privilege to drop every collection in it, so it is granted only where the policies give that
 * too, and is otherwise withheld and said so: the agent never grants more than the policies allow. Databases the agent
 * does not look after by name get only what can be granted on them as a whole.
 */
final class Planner
{
    static final String DEPLOYMENT = "deployment";
    static final String DATABASE = "database";
    static final String COLLECTION = "collection";

    private final Snapshot snapshot;
    private final Inventory inventory;
    private final Predicate<String> managed;
    private final Instant now;
    private final Consumer<String> withheld;

    /**
     * Plans with a snapshot.
     *
     * @param snapshot the policies
     * @param inventory what the deployment has
     * @param managed whether the agent grants within a database by collection
     * @param now the moment the policies are taken at
     * @param withheld told of each privilege a policy allows that MongoDB cannot give without giving more
     */
    Planner(Snapshot snapshot, Inventory inventory, Predicate<String> managed, Instant now, Consumer<String> withheld)
    {
        this.snapshot = snapshot;
        this.inventory = inventory;
        this.managed = managed;
        this.now = now;
        this.withheld = withheld;
    }

    /**
     * Plans what a user's role holds.
     *
     * @param account the user
     * @return the plan
     */
    Plan plan(Account account)
    {
        Map<Target, SortedSet<String>> privileges = new HashMap<>();
        for (String type : Actions.DEPLOYMENT_TYPES) {
            if (decide(deployment(account, type))) {
                add(privileges, Target.DEPLOYMENT, Actions.of(type));
            }
        }
        for (String database : inventory.databases()) {
            if (allowed(account, AccessRequest.ANY_ACCESS, database, null)) {
                database(account, database, privileges);
            }
        }
        return Plan.of(privileges);
    }

    private void database(Account account, String database, Map<Target, SortedSet<String>> privileges)
    {
        Target whole = Target.database(database);
        Set<String> wholeTypes = new HashSet<>();
        for (String type : Actions.COLLECTION_TYPES) {
            if (allowed(account, type, database, null) && !restricted(account, type, database)) {
                add(privileges, whole, Actions.of(type));
                wholeTypes.add(type);
            }
        }
        if (wholeTypes.contains(Actions.READ)) {
            add(privileges, whole, Actions.WHOLE_DATABASE_READ);
        }
        if (allowed(account, Actions.CREATE, database, null)) {
            add(privileges, whole, Actions.of(Actions.CREATE));
        }
        if (allowed(account, Actions.DROP_DATABASE, database, null)) {
            if (wholeTypes.contains(Actions.DROP)) {
                add(privileges, whole, Actions.of(Actions.DROP_DATABASE));
            }
            else {
                withheld.accept("dropping database " + database + " for user " + account.name() + ", as MongoDB lets only those who may drop every collection of a"
                        + " database drop it");
            }
        }
        if (!managed.test(database)) {
            return;
        }
        for (String collection : inventory.collections(database)) {
            for (String type : Actions.COLLECTION_TYPES) {
                if (!wholeTypes.contains(type) && allowed(account, type, database, collection)) {
                    add(privileges, Target.collection(database, collection), Actions.of(type));
                }
            }
        }
    }

    private static void add(Map<Target, SortedSet<String>> privileges, Target target, List<String> actions)
    {
        privileges.computeIfAbsent(target, ignored -> new TreeSet<>()).addAll(actions);
    }

    private boolean restricted(Account account, String type, String database)
    {
        return snapshot.restrictedBelow(request(account, type, database, null).build());
    }

    private boolean allowed(Account account, String type, String database, @Nullable String collection)
    {
        return decide(request(account, type, database, collection).build());
    }

    private boolean decide(AccessRequest request)
    {
        return snapshot.decide(request).allowed();
    }

    private AccessRequest deployment(Account account, String type)
    {
        return AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).resource(DEPLOYMENT, inventory.deployment()).time(now).build();
    }

    private AccessRequest.Builder request(Account account, String type, String database, @Nullable String collection)
    {
        AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).resource(DATABASE, database).time(now);
        if (collection != null) {
            request.resource(COLLECTION, collection);
        }
        return request;
    }
}
