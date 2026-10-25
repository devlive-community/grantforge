// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the policies of a snapshot into what each user is granted in ClickHouse, for the databases, tables and columns
 * the server has at the moment. An access type a policy allows on a database as a whole, with nothing below the database
 * that could take it away, is granted on the database, so that tables created later have it at once; otherwise it is
 * granted table by table, and for reading and writing column by column where the policies tell columns apart. The
 * server's access types are granted on the server. A table the user may read gets the row filter a policy sets for the
 * user.
 */
final class Planner
{
    private final Snapshot snapshot;
    private final Inventory inventory;
    private final Instant now;

    Planner(Snapshot snapshot, Inventory inventory, Instant now)
    {
        this.snapshot = snapshot;
        this.inventory = inventory;
        this.now = now;
    }

    /**
     * Plans what a user is granted.
     *
     * @param account the user
     * @return the plan
     */
    // A request and a grant per database, table and column is what planning by them is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Plan plan(Account account)
    {
        List<Grant> grants = new ArrayList<>();
        Map<TableRef, Plan.Filter> filters = new HashMap<>();
        for (String database : inventory.databases()) {
            if (!allowed(account, AccessRequest.ANY_ACCESS, database, null, null)) {
                continue;
            }
            for (String type : Privileges.DATABASE_TYPES) {
                if (allowed(account, type, database, null, null)) {
                    grants.add(Grant.database(type, database));
                }
            }
            List<TableRef> readable = new ArrayList<>();
            for (String type : Privileges.TABLE_TYPES) {
                tables(account, type, database, grants, null);
            }
            for (String type : Privileges.COLUMN_TYPES) {
                tables(account, type, database, grants, Privileges.SELECT.equals(type) ? readable : null);
            }
            for (TableRef table : readable) {
                RowFilter filter = snapshot.rowFilter(request(account, Privileges.SELECT, table.database(), table.table(), null));
                if (filter != null) {
                    filters.put(table, new Plan.Filter(filter.expression(), filter.policyId()));
                }
            }
        }
        for (String type : Privileges.SERVER_TYPES) {
            AccessRequest request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0]))
                    .resource("server", "*").time(now).build();
            if (snapshot.decide(request).allowed()) {
                grants.add(Grant.server(type));
            }
        }
        return new Plan(grants, filters);
    }

    /**
     * Grants an access type of tables or columns in a database: on the database as a whole when nothing below it is
     * decided otherwise, else table by table and, for columns, column by column.
     *
     * @param readable collects the tables some of whose columns are granted, or {@code null}
     */
    // A table reference and a grant per table, as for plan().
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void tables(Account account, String type, String database, List<Grant> grants, @Nullable List<TableRef> readable)
    {
        boolean columns = Privileges.COLUMN_TYPES.contains(type);
        if (allowed(account, type, database, null, null) && !restricted(account, type, database, null)) {
            grants.add(Grant.database(type, database));
            if (readable != null) {
                inventory.tables(database).forEach(table -> readable.add(new TableRef(database, table)));
            }
            return;
        }
        for (String name : inventory.tables(database)) {
            TableRef table = new TableRef(database, name);
            if (allowed(account, type, database, name, null) && !(columns && restricted(account, type, database, name))) {
                grants.add(Grant.table(type, table, List.of()));
            }
            else if (columns && allowed(account, AccessRequest.ANY_ACCESS, database, name, null)) {
                List<String> allowed = new ArrayList<>();
                for (String column : inventory.columns(table)) {
                    if (allowed(account, type, database, name, column)) {
                        allowed.add(column);
                    }
                }
                if (allowed.isEmpty()) {
                    continue;
                }
                grants.add(Grant.table(type, table, allowed));
            }
            else {
                continue;
            }
            if (readable != null) {
                readable.add(table);
            }
        }
    }

    private boolean allowed(Account account, String type, String database, @Nullable String table, @Nullable String column)
    {
        AgentDecision decision = snapshot.decide(request(account, type, database, table, column));
        return decision.allowed();
    }

    private boolean restricted(Account account, String type, String database, @Nullable String table)
    {
        return snapshot.restrictedBelow(request(account, type, database, table, null));
    }

    private AccessRequest request(Account account, String type, String database, @Nullable String table, @Nullable String column)
    {
        AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0]))
                .resource("database", database).time(now);
        if (table != null) {
            request.resource("table", table);
            if (column != null) {
                request.resource("column", column);
            }
        }
        return request.build();
    }
}
