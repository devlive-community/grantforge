// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import jakarta.validation.constraints.Size;
import org.devlive.grantforge.identity.application.UserProfileInput;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Details and departments of an account; blank texts clear a field.
 *
 * @param displayName the display name
 * @param email the e-mail address
 * @param primaryUnitId the primary department, or {@code null} for none
 * @param otherUnitIds further departments; only allowed together with a primary department; none if omitted
 * @param positionIds the positions the account holds; none if omitted
 */
public record UserProfileRequest(
        @Size(max = 128) @Nullable String displayName,
        @Size(max = 254) @Nullable String email,
        @Size(max = 20) @Nullable String primaryUnitId,
        @Size(max = 100) List<String> otherUnitIds,
        @Size(max = 100) List<String> positionIds)
{
    /** Copies the lists; JSON without them gives {@code null}, which means none. */
    @SuppressWarnings("ConstantValue")
    public UserProfileRequest
    {
        otherUnitIds = otherUnitIds == null ? List.of() : List.copyOf(otherUnitIds);
        positionIds = positionIds == null ? List.of() : List.copyOf(positionIds);
    }

    /**
     * Converts the request.
     *
     * @return the input
     */
    public UserProfileInput toInput()
    {
        String primary = primaryUnitId;
        return new UserProfileInput(displayName, email,
                primary == null || primary.isBlank() ? null : PathIds.parse(primary.trim(), "department"),
                otherUnitIds.stream().map(id -> PathIds.parse(id.trim(), "department")).toList(),
                positionIds.stream().map(id -> PathIds.parse(id.trim(), "position")).toList());
    }
}
