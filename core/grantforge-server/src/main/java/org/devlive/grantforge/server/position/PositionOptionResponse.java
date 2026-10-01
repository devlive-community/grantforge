// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import org.devlive.grantforge.identity.application.UserPosition;

/**
 * A position to choose for an account.
 *
 * @param id the position ID, as a string
 * @param name the name
 */
public record PositionOptionResponse(String id, String name)
{
    /**
     * Converts a position.
     *
     * @param position the position
     * @return the response
     */
    public static PositionOptionResponse from(UserPosition position)
    {
        return new PositionOptionResponse(Long.toString(position.positionId()), position.name());
    }
}
