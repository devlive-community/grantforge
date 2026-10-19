// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.DataMask;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Fits a masking policy to a column's type, so that a mask never breaks the query that reads the column. Trino wants a
 * mask of the column's own type: text masks apply to {@code varchar} and {@code char} columns and are cast back to the
 * column's type; the year mask applies to dates and timestamps; a custom expression is cast to the type; every other
 * column a text mask names reads as a {@code NULL} of its type, as does a mask the agent cannot read.
 */
final class Masks
{
    static final String NONE = "mask_none";
    static final String NULLIFY = "mask_null";
    static final String YEAR = "mask_date_show_year";
    static final String CUSTOM = "custom";

    private Masks()
    {
    }

    /**
     * Quotes a column for Trino SQL.
     *
     * @param column the column's name
     * @return the quoted identifier
     */
    static String quote(String column)
    {
        return '"' + column.replace("\"", "\"\"") + '"';
    }

    /**
     * Returns the expression that shows a column masked.
     *
     * @param mask the mask that applies
     * @param column the column's name
     * @param type the column's type, as Trino writes it, such as {@code varchar(20)}
     * @return the expression, or {@code null} to show the column as it is
     */
    static @Nullable String expression(DataMask mask, String column, String type)
    {
        String kind = mask.maskType();
        if (NONE.equals(kind)) {
            return null;
        }
        String nothing = "CAST(NULL AS " + type + ")";
        String expression;
        try {
            expression = mask.expression(quote(column));
        }
        catch (IllegalStateException broken) {
            return nothing;
        }
        if (expression == null || NULLIFY.equals(kind)) {
            return nothing;
        }
        String lower = type.toLowerCase(Locale.ROOT);
        boolean fits = switch (kind) {
            case CUSTOM -> true;
            case YEAR -> "date".equals(lower) || lower.startsWith("timestamp");
            default -> lower.startsWith("varchar") || lower.startsWith("char");
        };
        return fits ? "CAST((" + expression + ") AS " + type + ")" : nothing;
    }
}
