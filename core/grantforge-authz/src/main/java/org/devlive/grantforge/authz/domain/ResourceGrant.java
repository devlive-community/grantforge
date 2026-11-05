// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import java.time.Instant;

/** An explicit resource grant used in authorization derivation. */
public interface ResourceGrant
{
    /**
     * Returns the resource.
     *
     * @return resource ID
     */
    long getResourceId();

    /**
     * Returns the effect.
     *
     * @return allow or deny
     */
    GrantEffect getEffect();

    /**
     * Checks whether the grant applies.
     *
     * @param now current time
     * @return whether the grant applies
     */
    boolean appliesAt(Instant now);
}
