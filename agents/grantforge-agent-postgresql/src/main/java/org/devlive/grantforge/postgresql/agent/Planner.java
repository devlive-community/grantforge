// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

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

/**
 * Turns the policies of a snapshot into what each user is granted in PostgreSQL, for the databases, schemas, tables and
 * columns the server has at the moment. An access type a policy allows on a table as a whole, with nothing below the
 * table that could take it away, is granted on the table, so that columns added later have it at once; otherwise it is
 * granted column by column. Reaching what is granted needs {@code CONNECT} to the database and {@code USAGE} of the
 * schema, and inserting into a serial column {@code USAGE} of its sequence; they come with it. A table the user may
 * read gets the row filter a policy sets for the user; PostgreSQL filters the rows of tables only, so a view or the like
 * whose rows a policy filters is not readable at all.
 */
final class Planner
{
    private final Snapshot snapshot;
    private final Map<String, ServerState.DatabaseState> databases;
    private final Instant now;

    Planner(Snapshot snapshot, Map<String, ServerState.DatabaseState> databases, Instant now)
    {
        this.snapshot = snapshot;
        this.databases = databases;
        this.now = now;
    }

    /**
     * Plans what a user is granted.
     *
     * @param account the user
     * @return the plan
     */
    // A request and a privilege per database, schema, table and column is what planning by them is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Plan plan(Account account)
    {
        Map<String, Set<Privilege>> privileges = new HashMap<>();
        Map<String, Map<TableRef, Plan.Filter>> filters = new HashMap<>();
        List<String> notes = new ArrayList<>();
        for (ServerState.DatabaseState state : databases.values()) {
            String database = state.name();
            if (!allowed(account, AccessRequest.ANY_ACCESS, database, null, null, null)) {
                continue;
            }
            Set<Privilege> granted = privileges.computeIfAbsent(database, ignored -> new HashSet<>());
            Map<TableRef, Plan.Filter> filtered = filters.computeIfAbsent(database, ignored -> new HashMap<>());
            granted.add(Privilege.database(database, "CONNECT"));
            for (String type : Privilege.DATABASE_TYPES) {
                if (allowed(account, type, database, null, null, null)) {
                    granted.add(Privilege.database(database, Privilege.keyword(type)));
                }
            }
            for (String schema : state.inventory().schemas()) {
                if (!allowed(account, AccessRequest.ANY_ACCESS, database, schema, null, null)) {
                    continue;
                }
                granted.add(Privilege.schema(schema, "USAGE"));
                if (allowed(account, Privilege.CREATE, database, schema, null, null)) {
                    granted.add(Privilege.schema(schema, "CREATE"));
                }
                for (Inventory.Table table : state.inventory().tables(schema)) {
                    if (allowed(account, AccessRequest.ANY_ACCESS, database, schema, table.ref().table(), null)) {
                        table(account, database, table, granted, filtered, notes);
                    }
                }
            }
        }
        return new Plan(privileges, filters, notes);
    }

    // A privilege per column granted.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void table(Account account, String database, Inventory.Table table, Set<Privilege> granted, Map<TableRef, Plan.Filter> filtered,
            List<String> notes)
    {
        TableRef ref = table.ref();
        String schema = ref.schema();
        String name = ref.table();
        for (String type : Privilege.TABLE_TYPES) {
            if (allowed(account, type, database, schema, name, null)) {
                granted.add(Privilege.table(ref, Privilege.keyword(type)));
            }
        }
        List<Privilege> reading = new ArrayList<>();
        for (String type : Privilege.COLUMN_TYPES) {
            String keyword = Privilege.keyword(type);
            List<String> columns = new ArrayList<>();
            List<Privilege> those = Privilege.SELECT.equals(type) ? reading : new ArrayList<>();
            if (allowed(account, type, database, schema, name, null) && !snapshot.restrictedBelow(request(account, type, database, schema, name, null))) {
                those.add(Privilege.table(ref, keyword));
                columns.addAll(table.columns());
            }
            else {
                for (String column : table.columns()) {
                    if (allowed(account, type, database, schema, name, column)) {
                        those.add(Privilege.column(ref, column, keyword));
                        columns.add(column);
                    }
                }
            }
            if (!Privilege.SELECT.equals(type)) {
                granted.addAll(those);
            }
            if (Privilege.INSERT.equals(type)) {
                for (String column : columns) {
                    TableRef sequence = table.sequences().get(column);
                    if (sequence != null) {
                        granted.add(Privilege.sequence(sequence, "USAGE"));
                    }
                }
            }
        }
        if (reading.isEmpty()) {
            return;
        }
        RowFilter filter = snapshot.rowFilter(request(account, Privilege.SELECT, database, schema, name, null));
        if (filter == null) {
            granted.addAll(reading);
        }
        else if (table.filterable()) {
            granted.addAll(reading);
            filtered.put(ref, new Plan.Filter(filter.expression(), filter.policyId()));
        }
        else {
            notes.add("GrantForge policy " + filter.policyId() + " filters the rows of " + database + "." + ref + " for PostgreSQL user "
                    + account.name() + ", whose rows PostgreSQL cannot filter, as it is no table; the user may not read it");
        }
    }

    private boolean allowed(Account account, String type, String database, @Nullable String schema, @Nullable String table,
            @Nullable String column)
    {
        return snapshot.decide(request(account, type, database, schema, table, column)).allowed();
    }

    private AccessRequest request(Account account, String type, String database, @Nullable String schema, @Nullable String table,
            @Nullable String column)
    {
        AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0]))
                .resource("database", database).time(now);
        if (schema != null) {
            request.resource("schema", schema);
            if (table != null) {
                request.resource("table", table);
                if (column != null) {
                    request.resource("column", column);
                }
            }
        }
        return request.build();
    }
}
