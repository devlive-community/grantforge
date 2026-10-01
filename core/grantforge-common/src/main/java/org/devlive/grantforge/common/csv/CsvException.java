// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.csv;

import java.io.Serial;

/** A file that is not valid CSV. */
public final class CsvException
        extends RuntimeException
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final int line;

    /**
     * Creates the exception.
     *
     * @param line the 1-based line where the problem was found
     * @param detail what is wrong
     */
    public CsvException(int line, String detail)
    {
        super(detail + " (line " + line + ")");
        this.line = line;
    }

    /**
     * Returns where the problem was found.
     *
     * @return the 1-based line
     */
    public int getLine()
    {
        return line;
    }
}
