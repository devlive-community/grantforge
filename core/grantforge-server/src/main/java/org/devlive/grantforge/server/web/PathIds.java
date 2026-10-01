// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.web;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;

/**
 * Reads IDs from request paths. IDs travel as strings because they exceed JavaScript's safe integers; a
 * malformed one cannot name anything, so it is reported as not found.
 */
public final class PathIds
{
    private PathIds()
    {
    }

    /**
     * Parses an ID.
     *
     * @param id the path segment
     * @param what what the ID names, for the error detail
     * @return the ID
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the segment is not a number
     */
    public static long parse(String id, String what)
    {
        try {
            return Long.parseLong(id);
        }
        catch (NumberFormatException malformed) {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no " + what + " " + id, malformed);
        }
    }
}
