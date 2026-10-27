// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * One privilege of a role in one database, as PostgreSQL's access control lists hold it: on the database, a schema, a
 * table, a column of a table, or a sequence. The service type's access types stand for some of these privileges;
 * others are implied by what the policies give below them, as PostgreSQL needs {@code CONNECT} to a database and
 * {@code USAGE} of a schema to reach anything in them, and {@code USAGE} of a serial column's sequence to insert rows.
 *
 * @param kind what the privilege is on
 * @param schema the schema of a table, column or sequence, else {@code null}
 * @param object the database, the schema, the table or the sequence
 * @param column the column, for a column privilege, else {@code null}
 * @param keyword the privilege as PostgreSQL names it, such as {@code SELECT}
 */
record Privilege(Kind kind, @Nullable String schema, String object, @Nullable String column, String keyword)
{
    static final String SELECT = "select";
    static final String INSERT = "insert";
    static final String UPDATE = "update";
    static final String DELETE = "delete";
    static final String TRUNCATE = "truncate";
    static final String TRIGGER = "trigger";
    static final String CREATE = "create";
    static final String CREATE_SCHEMA = "create_schema";
    static final String TEMPORARY = "temporary";

    /** The access types of columns, which PostgreSQL grants on columns too. */
    static final List<String> COLUMN_TYPES = List.of(SELECT, INSERT, UPDATE);
    /** The access types of tables. */
    static final List<String> TABLE_TYPES = List.of(DELETE, TRUNCATE, TRIGGER);
    /** The access types of schemas. */
    static final List<String> SCHEMA_TYPES = List.of(CREATE);
    /** The access types of databases. */
    static final List<String> DATABASE_TYPES = List.of(CREATE_SCHEMA, TEMPORARY);

    private static final Comparator<@Nullable String> NULLS_FIRST = Comparator.nullsFirst(Comparator.<String>naturalOrder());

    /** By what they are on, then by privilege. */
    static final Comparator<Privilege> ORDER = Comparator.comparing(Privilege::kind).thenComparing(Privilege::schema, NULLS_FIRST)
            .thenComparing(Privilege::object).thenComparing(Privilege::column, NULLS_FIRST).thenComparing(Privilege::keyword);

    /** What a privilege is on. */
    enum Kind
    {
        DATABASE("DATABASE"), SCHEMA("SCHEMA"), TABLE("TABLE"), COLUMN("TABLE"), SEQUENCE("SEQUENCE");

        private final String target;

        Kind(String target)
        {
            this.target = target;
        }

        /** What statements name an object of the kind as, such as {@code TABLE}. */
        String target()
        {
            return target;
        }
    }

    /** A privilege on a database. */
    static Privilege database(String database, String keyword)
    {
        return new Privilege(Kind.DATABASE, null, database, null, keyword);
    }

    /** A privilege on a schema. */
    static Privilege schema(String schema, String keyword)
    {
        return new Privilege(Kind.SCHEMA, null, schema, null, keyword);
    }

    /** A privilege on a table as a whole. */
    static Privilege table(TableRef table, String keyword)
    {
        return new Privilege(Kind.TABLE, table.schema(), table.table(), null, keyword);
    }

    /** A privilege on a column of a table. */
    static Privilege column(TableRef table, String column, String keyword)
    {
        return new Privilege(Kind.COLUMN, table.schema(), table.table(), column, keyword);
    }

    /** A privilege on a sequence. */
    static Privilege sequence(TableRef sequence, String keyword)
    {
        return new Privilege(Kind.SEQUENCE, sequence.schema(), sequence.table(), null, keyword);
    }

    /**
     * Returns PostgreSQL's privilege for an access type.
     *
     * @param accessType the access type
     * @return such as {@code SELECT}
     * @throws IllegalArgumentException for an access type the service type does not have
     */
    static String keyword(String accessType)
    {
        return switch (accessType) {
            case SELECT, INSERT, UPDATE, DELETE, TRUNCATE, TRIGGER, CREATE, TEMPORARY -> accessType.toUpperCase(Locale.ROOT);
            case CREATE_SCHEMA -> "CREATE";
            default -> throw new IllegalArgumentException("PostgreSQL services have no access type " + accessType);
        };
    }

    /**
     * Returns the access type a privilege of PostgreSQL amounts to, as {@code aclexplode} names it.
     *
     * @param kind what it is on
     * @param privilege such as {@code SELECT}
     * @return the access type, or {@code null} for a privilege no access type stands for, such as {@code CONNECT}
     */
    static @Nullable String accessType(Kind kind, String privilege)
    {
        return switch (kind) {
            case DATABASE -> switch (privilege) {
                case "CREATE" -> CREATE_SCHEMA;
                case "TEMPORARY" -> TEMPORARY;
                default -> null;
            };
            case SCHEMA -> "CREATE".equals(privilege) ? CREATE : null;
            case TABLE, COLUMN -> switch (privilege) {
                case "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "TRIGGER" -> privilege.toLowerCase(Locale.ROOT);
                default -> null;
            };
            case SEQUENCE -> null;
        };
    }

    /** What the statement names the privilege's object as. */
    private String target()
    {
        String name = schema == null ? Sql.identifier(object) : Sql.identifier(schema) + "." + Sql.identifier(object);
        return kind.target() + " " + name;
    }

    /**
     * Returns the statements that grant or revoke privileges of a role: one per object, with the columns of a table
     * listed by privilege.
     *
     * @param grant whether to grant rather than revoke
     * @param role the role
     * @param privileges the privileges
     * @return the statements, in a stable order
     */
    // A list per object and privilege is what grouping them is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static List<String> statements(boolean grant, String role, Collection<Privilege> privileges)
    {
        Map<String, SortedMap<String, SortedSet<String>>> byTarget = new LinkedHashMap<>();
        for (Privilege privilege : privileges.stream().sorted(ORDER).toList()) {
            SortedMap<String, SortedSet<String>> keywords = byTarget.computeIfAbsent(privilege.target(), ignored -> new TreeMap<>());
            SortedSet<String> columns = keywords.computeIfAbsent(privilege.keyword(), ignored -> new TreeSet<>());
            String column = privilege.column();
            if (column != null) {
                columns.add(column);
            }
        }
        List<String> statements = new ArrayList<>(byTarget.size());
        String who = Sql.identifier(role);
        byTarget.forEach((target, keywords) -> {
            String list = keywords.entrySet().stream().map(entry -> entry.getKey() + (entry.getValue().isEmpty() ? ""
                    : " (" + Sql.identifiers(entry.getValue()) + ")")).collect(Collectors.joining(", "));
            statements.add(grant ? "GRANT " + list + " ON " + target + " TO " + who : "REVOKE " + list + " ON " + target + " FROM " + who);
        });
        return statements;
    }

    /**
     * Returns the privilege as messages name it.
     *
     * @return such as {@code SELECT (card) ON TABLE crm.orders}
     */
    @Override
    public String toString()
    {
        return keyword + (column == null ? "" : " (" + column + ")") + " ON " + kind.target() + " " + (schema == null ? "" : schema + ".") + object;
    }
}
