// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A shop's schema and policies for the tests, as the MySQL plugin defines the service type. */
public final class Fixtures
{
    /** The MySQL service type as snapshots carry it, with the plugin's mask transformers. */
    static final String DEFINITION = """
            {"resources": [
               {"name": "database", "parent": null, "matcher": "WILDCARD", "caseSensitive": true},
               {"name": "table", "parent": "database", "matcher": "WILDCARD", "caseSensitive": true},
               {"name": "column", "parent": "table", "matcher": "WILDCARD", "caseSensitive": false}],
             "accessTypes": [
               {"name": "select", "impliedGrants": []}, {"name": "insert", "impliedGrants": []}, {"name": "update", "impliedGrants": []},
               {"name": "delete", "impliedGrants": []}, {"name": "create", "impliedGrants": []}, {"name": "drop", "impliedGrants": []},
               {"name": "alter", "impliedGrants": []}, {"name": "index", "impliedGrants": []},
               {"name": "all", "impliedGrants": ["alter", "create", "delete", "drop", "index", "insert", "select", "update"]}],
             "conditions": [],
             "maskTypes": [
               {"name": "mask_show_last_4", "transformer": "CONCAT(REPEAT('x', GREATEST(CHAR_LENGTH({col}) - 4, 0)), RIGHT({col}, 4))"},
               {"name": "mask_hash", "transformer": "SHA2({col}, 256)"},
               {"name": "mask_null", "transformer": "NULL"},
               {"name": "mask_none", "transformer": "{col}"},
               {"name": "custom", "transformer": "{expr}"}]}
            """;

    private Fixtures()
    {
    }

    /** The shop's tables: sales has orders, items and customers; hr has salaries. */
    public static Catalog catalog()
    {
        return catalog(false);
    }

    /**
     * The shop's tables.
     *
     * @param folds whether the server folds names to lower case
     * @return the catalog
     */
    public static Catalog catalog(boolean folds)
    {
        Map<TableName, List<String>> tables = new HashMap<>();
        tables.put(new TableName("sales", "orders"), List.of("id", "customer_id", "card", "region", "total"));
        tables.put(new TableName("sales", "items"), List.of("order_id", "sku", "price"));
        tables.put(new TableName("sales", "customers"), List.of("id", "name", "email", "region"));
        tables.put(new TableName("hr", "salaries"), List.of("employee", "Amount"));
        Set<String> routines = Set.of("sales.discount");
        return new Catalog()
        {
            @Override
            public @Nullable List<String> columns(TableName table)
            {
                return tables.get(table);
            }

            @Override
            public boolean routine(String database, String name)
            {
                return routines.contains(database + "." + name.toLowerCase(java.util.Locale.ROOT));
            }

            @Override
            public boolean foldsNames()
            {
                return folds;
            }
        };
    }

    /** Builds a snapshot from policies. */
    public static final class Policy
    {
        private static final java.util.concurrent.atomic.AtomicLong VERSIONS = new java.util.concurrent.atomic.AtomicLong();

        private final List<String> policies = new ArrayList<>();
        private final long version = VERSIONS.incrementAndGet();
        private int next = 1;
        private boolean enabled = true;

        /**
         * Adds an access policy.
         *
         * @param database the database values
         * @param table the table values, or {@code null} for a database policy
         * @param column the column values, or {@code null} for a table policy
         * @param users the users it allows
         * @param accessTypes what it allows
         * @return this builder
         */
        public Policy allow(String database, @Nullable String table, @Nullable String column, List<String> users, String... accessTypes)
        {
            policies.add(policy("ACCESS", "NORMAL", resources(database, table, column), "\"allow\": [" + item(users, accessTypes, "") + "]"));
            return this;
        }

        /**
         * Adds a deny policy.
         *
         * @param database the database values
         * @param table the table values
         * @param column the column values
         * @param users the users it denies
         * @param accessTypes what it denies
         * @return this builder
         */
        public Policy deny(String database, String table, String column, List<String> users, String... accessTypes)
        {
            policies.add(policy("ACCESS", "NORMAL", resources(database, table, column), "\"deny\": [" + item(users, accessTypes, "") + "]"));
            return this;
        }

        /**
         * Adds a masking policy.
         *
         * @param database the database
         * @param table the table
         * @param column the column
         * @param user the user it masks for
         * @param maskType the mask type
         * @param value the custom expression, or {@code null}
         * @return this builder
         */
        public Policy mask(String database, String table, String column, String user, String maskType, @Nullable String value)
        {
            String extra = ", \"maskType\": \"" + maskType + "\"" + (value == null ? "" : ", \"maskValue\": " + json(value));
            policies.add(policy("DATA_MASK", "NORMAL", resources(database, table, column), "\"allow\": [" + item(List.of(user), new String[] {"select"}, extra) + "]"));
            return this;
        }

        /**
         * Adds a row filtering policy.
         *
         * @param database the database
         * @param table the table
         * @param user the user it filters for
         * @param filter the condition
         * @return this builder
         */
        public Policy filter(String database, String table, String user, String filter)
        {
            String extra = ", \"rowFilter\": " + json(filter);
            policies.add(policy("ROW_FILTER", "NORMAL", resources(database, table, null), "\"allow\": [" + item(List.of(user), new String[] {"select"}, extra) + "]"));
            return this;
        }

        /**
         * Says the service is not in use.
         *
         * @return this builder
         */
        public Policy disabled()
        {
            enabled = false;
            return this;
        }

        /**
         * Returns the snapshot.
         *
         * @return the snapshot
         */
        public Snapshot snapshot()
        {
            String json = "{\"format\": 1, \"service\": \"shop\", \"serviceType\": \"mysql\", \"serviceEnabled\": " + enabled
                    + ", \"policyVersion\": " + version + ", \"definition\": " + DEFINITION + ", \"policies\": [" + String.join(",", policies)
                    + "], \"roles\": {}, \"groups\": {}}";
            return Snapshot.parse(json.getBytes(StandardCharsets.UTF_8), Map.of());
        }

        /**
         * Returns the policies of the snapshot.
         *
         * @return the policies
         */
        public Policies policies()
        {
            return Policies.of(snapshot());
        }

        private String policy(String type, String priority, String resources, String items)
        {
            return "{\"id\": \"" + next++ + "\", \"name\": \"p\", \"type\": \"" + type + "\", \"priority\": \"" + priority
                    + "\", \"document\": {\"resources\": " + resources + ", " + items + "}}";
        }

        private static String resources(String database, @Nullable String table, @Nullable String column)
        {
            StringBuilder json = new StringBuilder("{").append(level("database", database));
            if (table != null) {
                json.append(", ").append(level("table", table));
            }
            if (column != null) {
                json.append(", ").append(level("column", column));
            }
            return json.append('}').toString();
        }

        private static String level(String name, String value)
        {
            return "\"" + name + "\": {\"values\": [" + json(value) + "], \"excludes\": false, \"recursive\": false}";
        }

        private static String item(List<String> users, String[] accessTypes, String extra)
        {
            List<String> quoted = users.stream().map(Policy::json).toList();
            List<String> types = List.of(accessTypes).stream().map(Policy::json).toList();
            return "{\"users\": [" + String.join(",", quoted) + "], \"groups\": [], \"roles\": [], \"accessTypes\": ["
                    + String.join(",", types) + "]" + extra + "}";
        }

        private static String json(String text)
        {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
