// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import static java.util.Objects.requireNonNull;

/**
 * A position an account holds.
 *
 * @param positionId the position
 * @param name the position's name
 */
public record UserPosition(long positionId, String name)
{
    /** Validates the values. */
    public UserPosition
    {
        requireNonNull(name, "name");
    }
}
