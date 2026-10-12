// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import org.apache.shardingsphere.database.protocol.mysql.constant.MySQLCharacterSets;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Locale;

/** The Java character sets of MySQL's, through ShardingSphere's table of MySQL collations. */
final class Charsets
{
    private Charsets()
    {
    }

    /**
     * Returns the character set of a collation a client signs in with.
     *
     * @param collation MySQL's collation id
     * @return the character set
     * @throws IllegalArgumentException for a collation the table does not have
     */
    static Charset byCollation(int collation)
    {
        MySQLCharacterSets known = MySQLCharacterSets.findById(collation);
        if (known == null || known.getCharset() == null) {
            throw new IllegalArgumentException("unknown collation " + collation);
        }
        return known.getCharset();
    }

    /**
     * Returns the character set of a name SET NAMES takes.
     *
     * @param name MySQL's name of it, such as {@code utf8mb4} or {@code latin1}
     * @return the character set, or {@code null} for one the table does not have
     */
    static @Nullable Charset byName(String name)
    {
        String wanted = name.toLowerCase(Locale.ROOT);
        if ("utf8".equals(wanted)) {
            wanted = "utf8mb3";
        }
        for (MySQLCharacterSets known : MySQLCharacterSets.values()) {
            String collation = known.name().toLowerCase(Locale.ROOT);
            // MySQL's utf8mb3 collations are named utf8_ in ShardingSphere's table.
            if ((collation.startsWith(wanted + "_") || ("utf8mb3".equals(wanted) && collation.startsWith("utf8_"))) && known.getCharset() != null) {
                return known.getCharset();
            }
        }
        return null;
    }
}
