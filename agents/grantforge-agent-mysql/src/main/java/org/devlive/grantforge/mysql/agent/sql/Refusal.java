// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Why the proxy does not let a statement reach the server, as the MySQL error the client receives: the code and SQL
 * state MySQL itself uses for the same kind of refusal, so clients and tools react as they would to the server.
 */
public final class Refusal
        extends Exception
{
    /** ER_DBACCESS_DENIED_ERROR: no access to a database. */
    public static final int DATABASE_DENIED = 1044;

    /** ER_NO_DB_ERROR: a table without a database while none is selected. */
    public static final int NO_DATABASE = 1046;

    /** ER_TABLEACCESS_DENIED_ERROR: a command denied on a table. */
    public static final int TABLE_DENIED = 1142;

    /** ER_COLUMNACCESS_DENIED_ERROR: a command denied on a column. */
    public static final int COLUMN_DENIED = 1143;

    /** ER_SPECIFIC_ACCESS_DENIED_ERROR: an operation the proxy does not allow at all. */
    public static final int NOT_ALLOWED = 1227;

    /** ER_PARSE_ERROR: a statement the proxy cannot read. */
    public static final int CANNOT_PARSE = 1064;

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String state;

    /**
     * Creates a refusal.
     *
     * @param code the MySQL error code
     * @param message what the client is told; it names no secrets
     */
    public Refusal(int code, String message)
    {
        this(code, message, null);
    }

    /**
     * Creates a refusal that a failure led to.
     *
     * @param code the MySQL error code
     * @param message what the client is told; it names no secrets
     * @param cause the failure, kept for the logs
     */
    public Refusal(int code, String message, @Nullable Throwable cause)
    {
        super(message, cause);
        this.code = code;
        this.state = switch (code) {
            case NO_DATABASE -> "3D000";
            case DATABASE_DENIED, TABLE_DENIED, COLUMN_DENIED, NOT_ALLOWED, CANNOT_PARSE -> "42000";
            default -> "HY000";
        };
    }

    /**
     * Returns the MySQL error code.
     *
     * @return the code
     */
    public int code()
    {
        return code;
    }

    /**
     * Returns the SQL state.
     *
     * @return five characters
     */
    public String state()
    {
        return state;
    }
}
