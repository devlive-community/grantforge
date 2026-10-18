// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.util.Bytes;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lists the namespaces, tables and column families whose names start with what was typed, within the namespaces and
 * tables a policy names already. Policy values are wildcards, {@code *} for any run of characters and {@code ?} for one;
 * names compare with case, as HBase keeps them. Tables are named without their namespace, which is a level of its own.
 *
 * @param resource the level
 * @param typed what was typed
 * @param namespaces the namespace patterns to look in
 * @param tables the table patterns to look in
 * @param limit the most names returned
 */
record HBaseLookup(String resource, String typed, List<Pattern> namespaces, List<Pattern> tables, int limit)
{
    /** The most values of a parent level a lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_PARENTS = 50;

    /** The most tables whose column families one lookup reads. */
    static final int MAX_TABLES = 50;

    private static final Pattern ANY = Pattern.compile(".*", Pattern.DOTALL);

    /**
     * Builds the lookup of one level.
     *
     * @param resource namespace, table or column_family
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most names returned
     * @return the lookup
     * @throws IllegalArgumentException for a level without lookups
     */
    static HBaseLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!HBaseProvider.NAMESPACE.equals(resource) && !HBaseProvider.TABLE.equals(resource) && !HBaseProvider.COLUMN_FAMILY.equals(resource)) {
            throw new IllegalArgumentException("HBase services look up namespaces, tables and column families, not " + resource);
        }
        return new HBaseLookup(resource, typed.strip(), patterns(context.get(HBaseProvider.NAMESPACE)), patterns(context.get(HBaseProvider.TABLE)),
                limit);
    }

    /** A policy's values as patterns; none, any or too many look everywhere. */
    private static List<Pattern> patterns(@Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_PARENTS || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return List.of(ANY);
        }
        return values.stream().map(value -> pattern(value.strip())).toList();
    }

    /**
     * Turns a policy's wildcard into a pattern.
     *
     * @param wildcard the value, with {@code *} for any run of characters and {@code ?} for one
     * @return the pattern
     */
    static Pattern pattern(String wildcard)
    {
        StringBuilder regex = new StringBuilder();
        for (char character : wildcard.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            }
            else if (character == '?') {
                regex.append('.');
            }
            else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static boolean matches(List<Pattern> patterns, String name)
    {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches());
    }

    /**
     * Runs the lookup.
     *
     * @param admin the cluster's administration API
     * @return the names, sorted, at most {@link #limit()}
     * @throws IOException when HBase fails a request
     */
    List<String> run(Admin admin) throws IOException
    {
        SortedSet<String> names = new TreeSet<>();
        if (HBaseProvider.NAMESPACE.equals(resource)) {
            for (NamespaceDescriptor namespace : admin.listNamespaceDescriptors()) {
                names.add(namespace.getName());
            }
        }
        else {
            List<TableName> found = tables(admin);
            if (HBaseProvider.TABLE.equals(resource)) {
                found.forEach(table -> names.add(table.getQualifierAsString()));
            }
            else {
                for (TableName table : found.subList(0, Math.min(found.size(), MAX_TABLES))) {
                    for (byte[] family : admin.getDescriptor(table).getColumnFamilyNames()) {
                        names.add(Bytes.toString(family));
                    }
                }
            }
        }
        List<String> matching = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(typed) && matching.size() < limit) {
                matching.add(name);
            }
        }
        return matching;
    }

    /** The tables of the namespaces the policy names; for column families, only those the policy names too. */
    private List<TableName> tables(Admin admin) throws IOException
    {
        List<TableName> found = new ArrayList<>();
        for (NamespaceDescriptor namespace : admin.listNamespaceDescriptors()) {
            if (!matches(namespaces, namespace.getName())) {
                continue;
            }
            for (TableName table : admin.listTableNamesByNamespace(namespace.getName())) {
                if (HBaseProvider.TABLE.equals(resource) || matches(tables, table.getQualifierAsString())) {
                    found.add(table);
                }
            }
        }
        return found;
    }
}
