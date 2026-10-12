// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

/** MySQL identifiers: what a quoted name means and how to quote one. */
final class Identifiers
{
    private Identifiers()
    {
    }

    /**
     * Reads a name as MySQL does: without its backticks (or double quotes), a doubled quote standing for one.
     *
     * @param name the name as written
     * @return the name
     */
    static String unquote(String name)
    {
        if (name.length() >= 2) {
            char first = name.charAt(0);
            if ((first == '`' || first == '"') && name.charAt(name.length() - 1) == first) {
                String quote = String.valueOf(first);
                return name.substring(1, name.length() - 1).replace(quote + quote, quote);
            }
        }
        return name;
    }

    /**
     * Quotes a name with backticks, whatever it holds.
     *
     * @param name the name
     * @return the quoted name
     */
    static String quote(String name)
    {
        return "`" + name.replace("`", "``") + "`";
    }
}
