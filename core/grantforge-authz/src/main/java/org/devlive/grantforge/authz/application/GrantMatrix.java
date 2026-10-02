// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A role's grants on one application and what they mean.
 *
 * @param roleId the role
 * @param applicationId the application
 * @param readOnly whether the grants cannot be changed (system roles allow whole modules instead)
 * @param grants the explicit grants, expired ones included
 * @param states every resource the grants allow, imply or deny, with reasons
 */
public record GrantMatrix(long roleId, long applicationId, boolean readOnly, List<Grant> grants, List<State> states)
{
    /** Copies the lists. */
    public GrantMatrix
    {
        grants = List.copyOf(requireNonNull(grants, "grants"));
        states = List.copyOf(requireNonNull(states, "states"));
    }

    /**
     * An explicit grant.
     *
     * @param resourceId the resource
     * @param effect allow or deny
     * @param expiresAt when it stops applying, or {@code null}
     * @param applies whether it applies now
     */
    public record Grant(long resourceId, GrantEffect effect, @Nullable Instant expiresAt, boolean applies)
    {
        /** Checks the effect. */
        public Grant
        {
            requireNonNull(effect, "effect");
        }
    }

    /**
     * What the grants mean for one resource.
     *
     * @param resourceId the resource
     * @param state allowed, implied or denied
     * @param explicit whether a grant sets it directly
     * @param reasons why a derived state holds
     */
    public record State(long resourceId, GrantDerivation.State state, boolean explicit, List<GrantDerivation.Reason> reasons)
    {
        /** Copies the reasons. */
        public State
        {
            requireNonNull(state, "state");
            reasons = List.copyOf(requireNonNull(reasons, "reasons"));
        }
    }
}
