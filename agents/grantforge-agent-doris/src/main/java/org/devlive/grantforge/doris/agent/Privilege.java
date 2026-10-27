// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

/**
 * One privilege on one object, as the agent grants it and as {@code SHOW ROLES} shows it: on a catalog, a database, a
 * table or a workload group as a whole. Column privileges are {@link Plan}'s own, as Doris shows them only per user.
 *
 * @param privilege Doris's name of it in upper case, such as {@code SELECT_PRIV}
 * @param target the object as {@code SHOW ROLES} names it: {@code internal.*.*}, {@code internal.sales.*},
 *        {@code internal.sales.orders}, or a workload group's name
 * @param workloadGroup whether the target is a workload group
 */
record Privilege(String privilege, String target, boolean workloadGroup)
{
    /** By target, then privilege, workload groups last. */
    static final Comparator<Privilege> ORDER = Comparator.comparing((Privilege privilege) -> privilege.workloadGroup()).thenComparing(Privilege::target)
            .thenComparing(Privilege::privilege);

    /** Normalizes the privilege's name. */
    Privilege
    {
        privilege = privilege.strip().toUpperCase(Locale.ROOT);
    }

    /** A privilege on every database of a catalog. */
    static Privilege catalog(String privilege, String catalog)
    {
        return new Privilege(privilege, catalog + ".*.*", false);
    }

    /** A privilege on every table of a database. */
    static Privilege database(String privilege, String catalog, String database)
    {
        return new Privilege(privilege, catalog + "." + database + ".*", false);
    }

    /** A privilege on a table. */
    static Privilege table(String privilege, TableRef table)
    {
        return new Privilege(privilege, table.toString(), false);
    }

    /** A privilege on a workload group, or on every one for {@code %}. */
    static Privilege workloadGroup(String privilege, String group)
    {
        return new Privilege(privilege, group, true);
    }

    /**
     * Returns the object as GRANT and REVOKE name it.
     *
     * @return such as {@code `internal`.`sales`.*} or {@code WORKLOAD GROUP 'normal'}, or {@code null} for a target
     *         whose name holds dots, which {@code SHOW ROLES} leaves ambiguous
     */
    @Nullable String on()
    {
        if (workloadGroup) {
            return "WORKLOAD GROUP " + literal(target);
        }
        String[] parts = target.split("\\.", -1);
        if (parts.length != 3) {
            return null;
        }
        StringBuilder on = new StringBuilder();
        for (int index = 0; index < 3; index++) {
            on.append(index == 0 ? "" : ".").append("*".equals(parts[index]) ? "*" : quote(parts[index]));
        }
        return on.toString();
    }

    /**
     * Returns the level of the object.
     *
     * @return catalog, database, table or workload_group
     */
    String level()
    {
        if (workloadGroup) {
            return "workload_group";
        }
        if (target.endsWith(".*.*")) {
            return "catalog";
        }
        return target.endsWith(".*") ? "database" : "table";
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
     * Quotes text as a string literal.
     *
     * @param text the text
     * @return it between single quotes, quotes and backslashes in it escaped
     */
    static String literal(String text)
    {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Override
    public String toString()
    {
        return privilege + " ON " + (workloadGroup ? "WORKLOAD GROUP " : "") + target;
    }
}
