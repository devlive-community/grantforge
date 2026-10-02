// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A data policy to add or change.
 *
 * @param entityCode the secured entity; ignored when changing a policy
 * @param action what it lets the role do
 * @param scope which rows
 * @param effect whether it allows or denies
 * @param condition the condition, for {@link DataScope#CONDITION} only
 * @param orgUnitIds the chosen departments, for {@link DataScope#CUSTOM_ORGS} only
 */
public record DataPolicyCommand(String entityCode, DataAction action, DataScope scope, GrantEffect effect, @Nullable JsonNode condition,
        List<Long> orgUnitIds)
{
    /** Checks and copies the values. */
    public DataPolicyCommand
    {
        requireNonNull(entityCode, "entityCode");
        requireNonNull(action, "action");
        requireNonNull(scope, "scope");
        requireNonNull(effect, "effect");
        orgUnitIds = List.copyOf(orgUnitIds);
    }
}
