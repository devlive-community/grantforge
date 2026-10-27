// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists the catalogs, databases, tables, columns or workload groups that start with what was typed, within the
 * catalogs, databases and tables a policy already names. Doris's {@code SHOW} statements list what the lookup user has
 * any privilege on, so a lookup shows what that user may see. Policy values of the levels above are wildcards; the
 * names they match are looked in, at most {@value #MAX_PARENTS} of them.
 *
 * @param resource the level looked up
 * @param typed what was typed, stripped
 * @param catalogs the catalog wildcards the policy names, or none for every catalog
 * @param databases the database wildcards, or none for every database
 * @param tables the table wildcards, or none for every table
 * @param limit the most values wanted
 */
record DorisLookup(String resource, String typed, List<String> catalogs, List<String> databases, List<String> tables, int limit)
{
    /** The most names of a level above that a lookup looks in. */
    static final int MAX_PARENTS = 100;

    /** The catalog that holds Doris's own tables, which a policy names when it names no other. */
    static final String INTERNAL = "internal";

    /** The databases Doris keeps for itself; policies do not decide them. */
    static final Set<String> SYSTEM_DATABASES = Set.of("information_schema", "mysql", "__internal_schema");

    /** Doris's error codes for a refused sign-in and a missing privilege, as MySQL numbers them. */
    private static final int ACCESS_DENIED_USER = 1045;
    private static final Set<Integer> ACCESS_DENIED = Set.of(1044, 1142, 1143, 1227);

    /** The release in the variable {@code version_comment}, such as {@code doris-4.1.5-release-917b20be838} for 4.1.5. */
    private static final Pattern RELEASE = Pattern.compile("doris-(\\d+\\.\\d+(?:\\.\\d+)*)");

    /** Copies the wildcards. */
    DorisLookup
    {
        catalogs = List.copyOf(catalogs);
        databases = List.copyOf(databases);
        tables = List.copyOf(tables);
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
    static DorisLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!List.of(DorisProvider.CATALOG, DorisProvider.DATABASE, DorisProvider.TABLE, DorisProvider.COLUMN, DorisProvider.WORKLOAD_GROUP)
                .contains(resource)) {
            throw new IllegalArgumentException("Doris services have no resource " + resource);
        }
        return new DorisLookup(resource, typed.strip(), values(context.get(DorisProvider.CATALOG)), values(context.get(DorisProvider.DATABASE)),
                values(context.get(DorisProvider.TABLE)), Math.max(1, limit));
    }

    private static List<String> values(@Nullable List<String> values)
    {
        return values == null ? List.of() : values.stream().map(String::strip).filter(value -> !value.isEmpty()).toList();
    }

    /**
     * Runs the lookup.
     *
     * @param client the cluster
     * @return the names, sorted, at most {@code limit}
     * @throws LookupException when no frontend can be reached, the sign-in is refused or a statement fails
     */
    List<String> run(DorisClient client)
    {
        try {
            return client.run(connection -> {
                try (Statement statement = connection.createStatement()) {
                    return names(statement);
                }
            });
        }
        catch (SQLException failed) {
            throw failure(failed);
        }
    }

    private List<String> names(Statement statement) throws SQLException
    {
        Set<String> found = new LinkedHashSet<>();
        switch (resource) {
            case DorisProvider.CATALOG -> add(found, catalogs(statement), true);
            case DorisProvider.WORKLOAD_GROUP -> add(found, workloadGroups(statement), true);
            case DorisProvider.DATABASE -> {
                for (String catalog : matching(catalogs(statement), catalogs, true)) {
                    add(found, databases(statement, catalog), true);
                }
            }
            case DorisProvider.TABLE -> {
                for (String[] database : databasesWithin(statement)) {
                    add(found, list(statement, "SHOW TABLES FROM " + quote(database[0]) + "." + quote(database[1]), 1), true);
                }
            }
            default -> {
                int looked = 0;
                for (String[] database : databasesWithin(statement)) {
                    String from = quote(database[0]) + "." + quote(database[1]);
                    for (String table : matching(list(statement, "SHOW TABLES FROM " + from, 1), tables, true)) {
                        looked++;
                        if (looked > MAX_PARENTS) {
                            return sorted(found);
                        }
                        add(found, list(statement, "SHOW COLUMNS FROM " + from + "." + quote(table), 1), false);
                    }
                }
            }
        }
        return sorted(found);
    }

    /** The databases of the catalogs the policy names that match the databases it names, as catalog and database. */
    private List<String[]> databasesWithin(Statement statement) throws SQLException
    {
        List<String[]> within = new ArrayList<>();
        for (String catalog : matching(catalogs(statement), catalogs, true)) {
            for (String database : matching(databases(statement, catalog), databases, true)) {
                if (within.size() == MAX_PARENTS) {
                    return within;
                }
                within.add(new String[] {catalog, database});
            }
        }
        return within;
    }

    /** Adds the names that start with what was typed. */
    private void add(Set<String> found, List<String> names, boolean caseSensitive)
    {
        String start = caseSensitive ? typed : typed.toLowerCase(Locale.ROOT);
        for (String name : names) {
            if ((caseSensitive ? name : name.toLowerCase(Locale.ROOT)).startsWith(start)) {
                found.add(name);
            }
        }
    }

    private List<String> sorted(Set<String> found)
    {
        return found.stream().sorted().limit(limit).toList();
    }

    /**
     * Returns the names that match any of the wildcards, or all names when there are none or one is {@code *}.
     *
     * @param names the names
     * @param wildcards the policy's values, with {@code *} for any run of characters and {@code ?} for one
     * @param caseSensitive whether names compare case-sensitively
     * @return the matching names, at most {@value #MAX_PARENTS}
     */
    static List<String> matching(List<String> names, List<String> wildcards, boolean caseSensitive)
    {
        List<Pattern> patterns = wildcards.stream().map(wildcard -> wildcard(wildcard, caseSensitive)).toList();
        return names.stream().filter(name -> patterns.isEmpty() || patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches()))
                .limit(MAX_PARENTS).toList();
    }

    /**
     * Turns a policy's wildcard into a pattern.
     *
     * @param wildcard the value
     * @param caseSensitive whether it matches case-sensitively
     * @return the pattern
     */
    static Pattern wildcard(String wildcard, boolean caseSensitive)
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
        return Pattern.compile(regex.toString(), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Lists the catalogs the user sees.
     *
     * @param statement a statement of the connection
     * @return their names, the internal catalog first
     * @throws SQLException when the frontend fails the statement
     */
    static List<String> catalogs(Statement statement) throws SQLException
    {
        List<String> names = new ArrayList<>(list(statement, "SHOW CATALOGS", 2));
        names.remove(INTERNAL);
        names.add(0, INTERNAL);
        return names;
    }

    private static List<String> databases(Statement statement, String catalog) throws SQLException
    {
        return list(statement, "SHOW DATABASES FROM " + quote(catalog), 1).stream().filter(name -> !SYSTEM_DATABASES.contains(name)).toList();
    }

    /** The workload groups, named in the column {@code Name} of each release's {@code SHOW WORKLOAD GROUPS}. */
    private static List<String> workloadGroups(Statement statement) throws SQLException
    {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet rows = statement.executeQuery("SHOW WORKLOAD GROUPS")) {
            int column = rows.findColumn("Name");
            while (rows.next()) {
                names.add(rows.getString(column));
            }
        }
        return List.copyOf(names);
    }

    /** One column of a statement's rows. */
    private static List<String> list(Statement statement, String sql, int column) throws SQLException
    {
        List<String> names = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                names.add(rows.getString(column));
            }
        }
        return names;
    }

    /**
     * Counts the rows whose first column is not one of some names.
     *
     * @param rows the rows
     * @param left the names not counted
     * @return the count
     * @throws SQLException when the rows cannot be read
     */
    static int count(ResultSet rows, Set<String> left) throws SQLException
    {
        int count = 0;
        while (rows.next()) {
            if (!left.contains(rows.getString(1))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Quotes a name for a statement.
     *
     * @param name the name
     * @return it between backquotes, backquotes in it doubled
     */
    static String quote(String name)
    {
        return "`" + name.replace("`", "``") + "`";
    }

    /**
     * Reads the release from the variable {@code version_comment}.
     *
     * @param version such as {@code doris version doris-4.1.5-release-917b20be838}, or {@code null}
     * @return such as {@code 2.1.11}, or the text as it is when it names none
     */
    static String release(@Nullable String version)
    {
        if (version == null) {
            return "of an unknown release";
        }
        Matcher matcher = RELEASE.matcher(version);
        return matcher.find() ? matcher.group(1) : version;
    }

    /**
     * Says why the frontend failed, as a lookup failure of the matching reason.
     *
     * @param failed what the driver threw
     * @return the failure
     */
    static LookupException failure(SQLException failed)
    {
        String message = message(failed);
        if (failed.getErrorCode() == ACCESS_DENIED_USER || failed instanceof SQLInvalidAuthorizationSpecException) {
            return new LookupException(LookupException.Reason.AUTHENTICATION_FAILED, "Doris refused the sign-in: " + message, failed);
        }
        if (ACCESS_DENIED.contains(failed.getErrorCode())) {
            return new LookupException(LookupException.Reason.ACCESS_DENIED, "Doris refused the lookup user: " + message, failed);
        }
        if (failed instanceof SQLNonTransientConnectionException || failed instanceof SQLTransientConnectionException
                || failed.getSQLState() != null && failed.getSQLState().startsWith("08")) {
            return new LookupException(LookupException.Reason.UNREACHABLE, "no Doris frontend can be reached: " + message, failed);
        }
        return new LookupException(LookupException.Reason.FAILED, "Doris failed the lookup: " + message, failed);
    }

    /** The first line of the driver's message, without the driver's own prefix. */
    private static String message(SQLException failed)
    {
        String text = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
        int line = text.indexOf('\n');
        text = (line < 0 ? text : text.substring(0, line)).strip();
        return text.replaceFirst("^\\(conn=\\d+\\)\\s*", "");
    }
}
