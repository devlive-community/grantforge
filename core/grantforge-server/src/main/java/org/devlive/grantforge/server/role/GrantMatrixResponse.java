// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.GrantMatrix;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A role's grants on one application and what they mean; IDs are strings because they exceed JavaScript's safe
 * integers.
 *
 * @param roleId the role
 * @param applicationId the application
 * @param readOnly whether the grants cannot be changed (system roles)
 * @param grants the explicit grants, expired ones included
 * @param states every resource the grants allow, imply or deny, with reasons
 */
public record GrantMatrixResponse(String roleId, String applicationId, boolean readOnly, List<Grant> grants, List<State> states)
{
    /** Copies the lists. */
    public GrantMatrixResponse
    {
        grants = List.copyOf(grants);
        states = List.copyOf(states);
    }

    /**
     * Converts a matrix.
     *
     * @param matrix the matrix
     * @return the response
     */
    public static GrantMatrixResponse from(GrantMatrix matrix)
    {
        return new GrantMatrixResponse(Long.toString(matrix.roleId()), Long.toString(matrix.applicationId()), matrix.readOnly(),
                matrix.grants().stream().map(grant -> new Grant(Long.toString(grant.resourceId()), grant.effect(), grant.expiresAt(),
                        grant.applies())).toList(),
                matrix.states().stream().map(state -> new State(Long.toString(state.resourceId()), state.state(), state.explicit(),
                        state.reasons().stream().map(reason -> new Reason(Long.toString(reason.resourceId()), reason.via())).toList()))
                        .toList());
    }

    /**
     * An explicit grant.
     *
     * @param resourceId the resource
     * @param effect allow or deny
     * @param expiresAt when it stops applying, or {@code null}
     * @param applies whether it applies now
     */
    public record Grant(String resourceId, GrantEffect effect, @Nullable Instant expiresAt, boolean applies)
    {
    }

    /**
     * What the grants mean for one resource.
     *
     * @param resourceId the resource
     * @param state allowed, implied or denied
     * @param explicit whether a grant sets it directly
     * @param reasons why a derived state holds
     */
    public record State(String resourceId, GrantDerivation.State state, boolean explicit, List<Reason> reasons)
    {
        /** Copies the reasons. */
        public State
        {
            reasons = List.copyOf(reasons);
        }
    }

    /**
     * Why a resource is implied or denied.
     *
     * @param resourceId the resource that causes it
     * @param via how: as an ancestor, a dependency, below a denial, or through a system role
     */
    public record Reason(String resourceId, GrantDerivation.Via via)
    {
    }
}
