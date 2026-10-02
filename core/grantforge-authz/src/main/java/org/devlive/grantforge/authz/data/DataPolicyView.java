// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.DataAction;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A data policy as stored.
 *
 * @param id the policy's id
 * @param roleId the role
 * @param entityCode the secured entity
 * @param action what it lets the role do
 * @param scope which rows
 * @param effect whether it allows or denies
 * @param condition the condition as JSON, or {@code null}
 * @param orgUnitIds the chosen departments
 * @param updatedAt when it last changed
 */
public record DataPolicyView(long id, long roleId, String entityCode, DataAction action, DataScope scope, GrantEffect effect,
        @Nullable String condition, List<Long> orgUnitIds, Instant updatedAt)
{
    /** Copies the departments. */
    public DataPolicyView
    {
        orgUnitIds = List.copyOf(orgUnitIds);
    }
}
