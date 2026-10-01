// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Details of an account an administrator sets; values are validated by the service.
 *
 * @param displayName the display name; blank clears it
 * @param email the e-mail address; blank clears it
 * @param primaryUnitId the primary department, or {@code null} for none
 * @param otherUnitIds further departments; only allowed together with a primary department; copied
 * @param positionIds the positions the account holds; copied
 */
public record UserProfileInput(@Nullable String displayName, @Nullable String email, @Nullable Long primaryUnitId,
        List<Long> otherUnitIds, List<Long> positionIds)
{
    /** Copies the departments and positions. */
    public UserProfileInput
    {
        otherUnitIds = List.copyOf(requireNonNull(otherUnitIds, "otherUnitIds"));
        positionIds = List.copyOf(requireNonNull(positionIds, "positionIds"));
    }
}
