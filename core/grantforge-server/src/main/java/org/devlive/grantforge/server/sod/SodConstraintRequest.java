// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.SodConstraintCommand;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * A separation-of-duties constraint to add or change; the code is only taken when adding.
 *
 * @param code the code, unique in the tenant
 * @param name the name
 * @param description a longer explanation
 * @param roleIds the mutually exclusive roles, 2 to 50
 * @param maxRoles how many of them one account may hold; 1 unless said otherwise
 * @param mode what a conflict does; enforced unless said otherwise
 * @param enabled whether the constraint applies; on unless said otherwise
 */
public record SodConstraintRequest(
        @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 512) @Nullable String description,
        @NotNull @Size(max = 50) @Nullable List<@NotNull @Size(max = 20) String> roleIds,
        @Nullable Integer maxRoles,
        @Nullable SodMode mode,
        @Nullable Boolean enabled)
{
    /**
     * Turns the request into a command.
     *
     * @return the command
     */
    public SodConstraintCommand command()
    {
        List<String> ids = roleIds == null ? List.of() : roleIds;
        return new SodConstraintCommand(code == null ? "" : code.strip(), String.valueOf(name), description,
                new LinkedHashSet<>(ids.stream().map(id -> PathIds.parse(id.strip(), "role")).toList()), maxRoles == null ? 1 : maxRoles,
                mode == null ? SodMode.ENFORCE : mode, enabled == null || enabled);
    }
}
