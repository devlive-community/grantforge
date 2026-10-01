// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.devlive.grantforge.identity.application.UserDetail;

import java.util.List;

/**
 * An account with all its departments.
 *
 * @param user the account
 * @param memberships its departments, the primary one first
 */
public record UserDetailResponse(UserResponse user, List<Membership> memberships)
{
    /** Copies the memberships. */
    public UserDetailResponse
    {
        memberships = List.copyOf(memberships);
    }

    /**
     * A department of the account.
     *
     * @param unitId the department ID
     * @param unitName the department's name
     * @param primary whether it is the primary department
     */
    public record Membership(String unitId, String unitName, boolean primary)
    {
    }

    /**
     * Converts a detail.
     *
     * @param detail the detail
     * @return the response
     */
    public static UserDetailResponse from(UserDetail detail)
    {
        return new UserDetailResponse(UserResponse.from(detail.summary()), detail.memberships().stream()
                .map(member -> new Membership(Long.toString(member.unitId()), member.unitName(), member.primary()))
                .toList());
    }
}
