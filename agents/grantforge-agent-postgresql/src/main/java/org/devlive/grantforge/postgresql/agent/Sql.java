// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.postgresql.core.NativeQuery;
import org.postgresql.core.Parser;
import org.postgresql.core.Utils;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Names and text in PostgreSQL statements, quoted by PostgreSQL's own driver. */
final class Sql
{
    private Sql()
    {
    }

    /**
     * Checks that a text holds one statement as PostgreSQL's driver splits statements: it would send each statement of
     * a text with several on its own, so a row filter could otherwise end its statement and add others.
     *
     * @param sql the text
     * @param parameters whether {@code ?} in it are parameters
     * @throws SQLException with SQLSTATE 42601, a syntax error, when it holds more than one statement
     */
    static void single(String sql, boolean parameters) throws SQLException
    {
        List<NativeQuery> statements = Parser.parseJdbcSql(sql, true, parameters, true, false, false);
        if (statements.size() > 1) {
            throw new SQLException("the GrantForge agent sends one statement at a time; this holds " + statements.size() + ": " + sql, "42601");
        }
    }

    /**
     * Quotes a name.
     *
     * @param name such as {@code orders} or {@code Order "Items"}
     * @return such as {@code "orders"} or {@code "Order ""Items"""}
     * @throws IllegalArgumentException for a name PostgreSQL cannot hold, such as one with a NUL character
     */
    static String identifier(String name)
    {
        try {
            return Utils.escapeIdentifier(null, name).toString();
        }
        catch (SQLException invalid) {
            throw new IllegalArgumentException("PostgreSQL cannot name " + name, invalid);
        }
    }

    /**
     * Quotes names, separated by commas.
     *
     * @param names the names
     * @return such as {@code "id", "region"}
     */
    static String identifiers(Collection<String> names)
    {
        return names.stream().map(Sql::identifier).collect(Collectors.joining(", "));
    }

    /**
     * Quotes text, as standard_conforming_strings, which PostgreSQL has on by default since 9.1, reads it.
     *
     * @param text the text
     * @return such as {@code 'it''s'}
     * @throws IllegalArgumentException for text PostgreSQL cannot hold, such as one with a NUL character
     */
    static String literal(String text)
    {
        try {
            return "'" + Utils.escapeLiteral(null, text, true) + "'";
        }
        catch (SQLException invalid) {
            throw new IllegalArgumentException("PostgreSQL cannot hold " + text, invalid);
        }
    }
}
