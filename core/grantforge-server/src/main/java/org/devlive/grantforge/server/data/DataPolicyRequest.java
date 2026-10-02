// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.data.DataPolicyCommand;
import org.devlive.grantforge.authz.domain.DataAction;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

/**
 * A data policy to add or change.
 *
 * @param entityCode the secured entity, such as {@code user}; ignored when changing a policy
 * @param action what the policy lets the role do
 * @param scope which rows
 * @param effect whether it allows or denies; allows unless said otherwise
 * @param condition the condition, for the condition scope only; see the data entities for fields, operators and variables
 * @param orgUnitIds the chosen departments, for the chosen-departments scope only
 */
public record DataPolicyRequest(
        @NotNull @Size(max = 64) @Nullable String entityCode,
        @NotNull @Nullable DataAction action,
        @NotNull @Nullable DataScope scope,
        @Nullable GrantEffect effect,
        @Nullable JsonNode condition,
        @Size(max = 50) List<@Pattern(regexp = "\\d{1,19}") String> orgUnitIds)
{
    /** Copies the departments; JSON without them chooses none. */
    @SuppressWarnings("ConstantValue")
    public DataPolicyRequest
    {
        orgUnitIds = orgUnitIds == null ? List.of() : List.copyOf(orgUnitIds.stream().filter(Objects::nonNull).toList());
    }

    /**
     * Turns the request into a command.
     *
     * @return the command
     */
    public DataPolicyCommand command()
    {
        return new DataPolicyCommand(String.valueOf(entityCode), requireNonNullElse(action, DataAction.READ),
                requireNonNullElse(scope, DataScope.SELF), effect == null ? GrantEffect.ALLOW : effect, condition,
                orgUnitIds.stream().map(Long::valueOf).toList());
    }
}
