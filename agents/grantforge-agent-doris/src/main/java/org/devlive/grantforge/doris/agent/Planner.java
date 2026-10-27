// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
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
 * Turns the policies of a snapshot into what each user is granted in Doris, for the catalogs, databases, tables, columns
 * and workload groups the cluster has at the moment.
 *
 * <p>Reading, loading and showing views are granted on a catalog or database as a whole where a policy allows them
 * there and nothing below could take them away, so that tables created later have them at once; otherwise table by
 * table, and reading column by column where the policies tell columns apart. Altering is granted table by table, as
 * Doris's privilege on a database would also rename it. Doris's privilege to create databases in a catalog is the
 * privilege to create tables in all of them, and its privilege to drop a database the privilege to drop all its tables,
 * so each is granted only where the policies give the wider privilege too, and is otherwise withheld and said so: the
 * agent never grants more than the policies allow. Catalogs the agent does not look after by name get only what can be
 * granted on them as a whole. A table the user may read gets the row filter a policy sets for the user.
 */
final class Planner
{
    private final Snapshot snapshot;
    private final Inventory inventory;
    private final Predicate<String> managed;
    private final Instant now;
    private final Consumer<String> withheld;

    /**
     * Plans with a snapshot.
     *
     * @param snapshot the policies
     * @param inventory what the cluster has
     * @param managed whether the agent grants within a catalog by database and table
     * @param now the moment the policies are taken at
     * @param withheld told of each privilege a policy allows that Doris cannot give without giving more
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
     * Plans what a user is granted.
     *
     * @param account the user
     * @return the plan
     */
    // A request, a privilege and a set per catalog, database, table and column is what planning by them is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Plan plan(Account account)
    {
        List<Privilege> privileges = new ArrayList<>();
        Map<TableRef, SortedSet<String>> columns = new HashMap<>();
        Map<TableRef, Plan.Filter> filters = new HashMap<>();
        for (String catalog : inventory.catalogs()) {
            if (!allowed(account, AccessRequest.ANY_ACCESS, catalog, null, null, null)) {
                continue;
            }
            Set<String> wholeCatalog = new HashSet<>();
            for (String type : Privileges.LIFTED_TYPES) {
                if (whole(account, type, catalog, null)) {
                    privileges.add(Privilege.catalog(Privileges.privilege(type), catalog));
                    wholeCatalog.add(type);
                }
            }
            boolean createEverywhere = whole(account, Privileges.CREATE, catalog, null);
            if (allowed(account, Privileges.CREATE_DATABASE, catalog, null, null, null)) {
                if (createEverywhere && !restricted(account, Privileges.CREATE_DATABASE, catalog, null)) {
                    privileges.add(Privilege.catalog("CREATE_PRIV", catalog));
                    wholeCatalog.add(Privileges.CREATE);
                }
                else {
                    withheld.accept("creating databases in catalog " + catalog + " for user " + account.name() + ", as Doris lets only those who"
                            + " may create tables in every database of a catalog create databases in it");
                }
            }
            if (!managed.test(catalog)) {
                continue;
            }
            for (String database : inventory.databases(catalog)) {
                if (allowed(account, AccessRequest.ANY_ACCESS, catalog, database, null, null)) {
                    database(account, catalog, database, wholeCatalog, privileges, columns, filters);
                }
            }
        }
        for (String group : groups(account)) {
            privileges.add(Privilege.workloadGroup("USAGE_PRIV", group));
        }
        return new Plan(privileges, columns, filters);
    }

    // A table reference, a privilege and a set per table and column, as for plan().
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void database(Account account, String catalog, String database, Set<String> wholeCatalog, List<Privilege> privileges,
            Map<TableRef, SortedSet<String>> columns, Map<TableRef, Plan.Filter> filters)
    {
        Set<String> whole = new HashSet<>(wholeCatalog);
        if (!whole.contains(Privileges.CREATE) && allowed(account, Privileges.CREATE, catalog, database, null, null)) {
            privileges.add(Privilege.database("CREATE_PRIV", catalog, database));
        }
        for (String type : Privileges.LIFTED_TYPES) {
            if (!whole.contains(type) && whole(account, type, catalog, database)) {
                privileges.add(Privilege.database(Privileges.privilege(type), catalog, database));
                whole.add(type);
            }
        }
        if (allowed(account, Privileges.DROP_DATABASE, catalog, database, null, null)) {
            if (whole(account, Privileges.DROP, catalog, database) && !restricted(account, Privileges.DROP_DATABASE, catalog, database)) {
                privileges.add(Privilege.database("DROP_PRIV", catalog, database));
                whole.add(Privileges.DROP);
            }
            else {
                withheld.accept("dropping database " + catalog + "." + database + " for user " + account.name() + ", as Doris lets only those who"
                        + " may drop every table of a database drop it");
            }
        }
        for (String name : inventory.tables(catalog, database)) {
            TableRef table = new TableRef(catalog, database, name);
            if (!allowed(account, AccessRequest.ANY_ACCESS, catalog, database, name, null)) {
                continue;
            }
            for (String type : List.of(Privileges.LOAD, Privileges.ALTER, Privileges.DROP, Privileges.SHOW_VIEW)) {
                if (!whole.contains(type) && allowed(account, type, catalog, database, name, null)) {
                    privileges.add(Privilege.table(Privileges.privilege(type), table));
                }
            }
            boolean readable = whole.contains(Privileges.SELECT);
            if (!readable && whole(account, Privileges.SELECT, catalog, database, name)) {
                privileges.add(Privilege.table("SELECT_PRIV", table));
                readable = true;
            }
            else if (!readable) {
                SortedSet<String> allowed = new TreeSet<>();
                for (String column : inventory.columns(table)) {
                    if (allowed(account, Privileges.SELECT, catalog, database, name, column)) {
                        allowed.add(column);
                    }
                }
                if (!allowed.isEmpty()) {
                    columns.put(table, allowed);
                    readable = true;
                }
            }
            if (readable) {
                RowFilter filter = snapshot.rowFilter(request(account, Privileges.SELECT, catalog, database, name, null).build());
                if (filter != null) {
                    filters.put(table, new Plan.Filter(filter.expression(), filter.policyId()));
                }
            }
        }
    }

    /**
     * The workload groups the user may run queries in, by name: a policy on every group is granted group by group too,
     * as no request can ask about groups that do not exist yet.
     */
    private List<String> groups(Account account)
    {
        return inventory.workloadGroups().stream().filter(group -> decide(workloadGroup(account, group))).toList();
    }

    private AccessRequest workloadGroup(Account account, String group)
    {
        return AccessRequest.builder(account.name(), Privileges.USAGE).groups(account.groups().toArray(new String[0])).resource("workload_group", group)
                .time(now).build();
    }

    /** Whether an access type is allowed on a container and nothing below it could take it away. */
    private boolean whole(Account account, String type, String catalog, @Nullable String database)
    {
        return allowed(account, type, catalog, database, null, null) && !restricted(account, type, catalog, database);
    }

    /** Whether reading a table is allowed and no column of it could be taken away. */
    private boolean whole(Account account, String type, String catalog, String database, String table)
    {
        return allowed(account, type, catalog, database, table, null) && !snapshot.restrictedBelow(request(account, type, catalog, database, table,
                null).build());
    }

    private boolean restricted(Account account, String type, String catalog, @Nullable String database)
    {
        return snapshot.restrictedBelow(request(account, type, catalog, database, null, null).build());
    }

    private boolean allowed(Account account, String type, String catalog, @Nullable String database, @Nullable String table, @Nullable String column)
    {
        return decide(request(account, type, catalog, database, table, column).build());
    }

    private boolean decide(AccessRequest request)
    {
        return snapshot.decide(request).allowed();
    }

    private AccessRequest.Builder request(Account account, String type, String catalog, @Nullable String database, @Nullable String table,
            @Nullable String column)
    {
        AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0]))
                .resource("catalog", catalog).time(now);
        if (database != null) {
            request.resource("database", database);
            if (table != null) {
                request.resource("table", table);
                if (column != null) {
                    request.resource("column", column);
                }
            }
        }
        return request;
    }
}
