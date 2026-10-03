// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.AuthorizationInsight;
import org.devlive.grantforge.authz.application.GrantChange;
import org.devlive.grantforge.authz.application.Simulation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Changes to try on an account without making them. IDs are strings, as they exceed JavaScript's safe integers.
 *
 * @param accountId the account; the signed-in user if absent
 * @param addRoles roles the account would hold directly
 * @param removeRoles roles the account would no longer hold
 * @param grants grant changes of roles
 */
public record SimulationRequest(@Pattern(regexp = "\\d{1,19}") @Nullable String accountId,
        @Size(max = AuthorizationInsight.MAX_CHANGES) List<@NotNull @Pattern(regexp = "\\d{1,19}") String> addRoles,
        @Size(max = AuthorizationInsight.MAX_CHANGES) List<@NotNull @Pattern(regexp = "\\d{1,19}") String> removeRoles,
        @Size(max = AuthorizationInsight.MAX_CHANGES) List<@Valid @NotNull Grant> grants)
{
    /** Copies the lists; JSON without one gives {@code null}, which means none. */
    @SuppressWarnings("ConstantValue")
    public SimulationRequest
    {
        addRoles = addRoles == null ? List.of() : List.copyOf(addRoles);
        removeRoles = removeRoles == null ? List.of() : List.copyOf(removeRoles);
        grants = grants == null ? List.of() : List.copyOf(grants);
    }

    /**
     * Converts the validated request.
     *
     * @return the simulation
     */
    public Simulation toSimulation()
    {
        return new Simulation(addRoles.stream().map(Long::parseLong).toList(), removeRoles.stream().map(Long::parseLong).toList(),
                grants.stream().map(Grant::toChange).toList());
    }

    /**
     * A grant change of one role.
     *
     * @param roleId the role
     * @param resourceId the resource
     * @param effect allow or deny; absent to take the grant back
     */
    public record Grant(@NotNull @Pattern(regexp = "\\d{1,19}") @Nullable String roleId,
            @NotNull @Pattern(regexp = "\\d{1,19}") @Nullable String resourceId, @Nullable GrantEffect effect)
    {
        Simulation.RoleGrantChange toChange()
        {
            return new Simulation.RoleGrantChange(Long.parseLong(requireNonNull(roleId, "roleId")),
                    new GrantChange(Long.parseLong(requireNonNull(resourceId, "resourceId")), effect, null));
        }
    }
}
