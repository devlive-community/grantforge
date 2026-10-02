// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.DataPolicyView;
import org.devlive.grantforge.authz.domain.DataAction;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

/**
 * A data policy of a role. IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the policy's id
 * @param roleId the role
 * @param entityCode the secured entity
 * @param action what it lets the role do
 * @param scope which rows
 * @param effect whether it allows or denies
 * @param condition the condition, or {@code null}
 * @param orgUnitIds the chosen departments
 * @param updatedAt when it last changed
 */
public record DataPolicyResponse(String id, String roleId, String entityCode, DataAction action, DataScope scope, GrantEffect effect,
        @Nullable JsonNode condition, List<String> orgUnitIds, Instant updatedAt)
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Copies the departments. */
    public DataPolicyResponse
    {
        orgUnitIds = List.copyOf(orgUnitIds);
    }

    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static DataPolicyResponse from(DataPolicyView view)
    {
        String condition = view.condition();
        return new DataPolicyResponse(Long.toString(view.id()), Long.toString(view.roleId()), view.entityCode(), view.action(),
                view.scope(), view.effect(), condition == null ? null : JSON.readTree(condition),
                view.orgUnitIds().stream().map(String::valueOf).toList(), view.updatedAt());
    }
}
