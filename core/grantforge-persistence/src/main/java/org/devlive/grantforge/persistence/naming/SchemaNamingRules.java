// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.naming;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Naming rules for database identifiers that hold on H2, MySQL, MariaDB, PostgreSQL, Oracle and SQL Server.
 *
 * <ul>
 *   <li>At most {@value #MAX_LENGTH} characters: Oracle's limit before 12.2 and a safe bound everywhere.</li>
 *   <li>Lowercase {@code snake_case} starting with a letter: unquoted identifiers are folded to upper case by
 *       Oracle and H2 and to lower case by PostgreSQL, so mixed case would require quoting everywhere.</li>
 *   <li>Not a reserved word of any supported database (see {@link #RESERVED_WORDS}).</li>
 *   <li>Tables start with {@value #TABLE_PREFIX} so GrantForge tables never collide in a shared schema.</li>
 * </ul>
 */
public final class SchemaNamingRules
{
    /** Longest identifier accepted. */
    public static final int MAX_LENGTH = 30;
    /** Prefix of every GrantForge table. */
    public static final String TABLE_PREFIX = "gf_";

    /**
     * Words reserved by at least one supported database that are plausible as table or column names.
     * Curated from the vendor reserved-word lists; extend it when a new database is supported.
     */
    public static final Set<String> RESERVED_WORDS = Set.of(
            "access", "add", "all", "alter", "analyse", "analyze", "and", "any", "array", "as", "asc",
            "asymmetric", "audit", "authorization", "begin", "between", "bigint", "binary", "blob", "both", "by",
            "call", "cascade", "case", "cast", "char", "character", "check", "clob", "cluster", "collate",
            "column", "comment", "commit", "compress", "condition", "connect", "constraint", "contains",
            "continue", "convert", "create", "cross", "current", "current_date", "current_time",
            "current_timestamp", "current_user", "cursor", "database", "databases", "date", "day", "dec",
            "decimal", "declare", "default", "delete", "desc", "describe", "distinct", "double", "drop", "else",
            "end", "escape", "except", "exclusive", "exec", "execute", "exists", "exit", "explain", "false",
            "fetch", "file", "float", "for", "foreign", "from", "full", "function", "grant", "group", "having",
            "identity", "if", "immediate", "in", "increment", "index", "initial", "inner", "insert", "int",
            "integer", "intersect", "interval", "into", "is", "join", "key", "keys", "kill", "leading", "left",
            "level", "like", "limit", "lines", "load", "lock", "long", "loop", "match", "maxextents", "merge",
            "minus", "mode", "modify", "month", "natural", "noaudit", "nocompress", "not", "nowait", "null",
            "number", "of", "offline", "offset", "on", "online", "open", "option", "or", "order", "outer", "over",
            "partition", "pctfree", "percent", "plan", "precision", "primary", "prior", "privileges", "procedure",
            "public", "range", "raw", "read", "real", "references", "rename", "repeat", "replace", "resource",
            "restrict", "return", "revoke", "right", "row", "rowid", "rownum", "rows", "schema", "second",
            "select", "session", "session_user", "set", "share", "size", "smallint", "some", "start",
            "successful", "synonym", "sysdate", "system_user", "table", "then", "time", "timestamp", "to", "top",
            "trailing", "trigger", "true", "truncate", "uid", "union", "unique", "update", "usage", "user",
            "using", "validate", "value", "values", "varchar", "varchar2", "view", "when", "whenever", "where",
            "while", "window", "with", "year");

    private static final Pattern SNAKE_CASE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");

    /** Kind of identifier, which decides the prefix rule. */
    public enum Kind
    {
        /** A table name; must start with {@value SchemaNamingRules#TABLE_PREFIX}. */
        TABLE,
        /** A column name. */
        COLUMN
    }

    private SchemaNamingRules()
    {
    }

    /**
     * Checks one identifier.
     *
     * @param kind what the identifier names
     * @param identifier the identifier; {@code null} or blank is reported as a violation
     * @return human-readable violations; empty when the identifier is valid
     */
    public static List<String> violations(Kind kind, @Nullable String identifier)
    {
        List<String> problems = new ArrayList<>();
        if (identifier == null || identifier.isBlank()) {
            problems.add(kind.name().toLowerCase(Locale.ROOT) + " name must not be blank");
            return problems;
        }
        String label = kind.name().toLowerCase(Locale.ROOT) + " '" + identifier + "'";
        if (identifier.length() > MAX_LENGTH) {
            problems.add(label + " is longer than " + MAX_LENGTH + " characters");
        }
        if (!SNAKE_CASE.matcher(identifier).matches()) {
            problems.add(label + " must be lowercase snake_case starting with a letter");
        }
        if (RESERVED_WORDS.contains(identifier.toLowerCase(Locale.ROOT))) {
            problems.add(label + " is a reserved word in at least one supported database");
        }
        if (kind == Kind.TABLE && !identifier.startsWith(TABLE_PREFIX)) {
            problems.add(label + " must start with '" + TABLE_PREFIX + "'");
        }
        return problems;
    }
}
