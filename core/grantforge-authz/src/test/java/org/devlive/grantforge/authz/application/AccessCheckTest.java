// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AccessCheckTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void needsAKindAndACode()
    {
        assertThat(new AccessCheck(AccessKind.PERMISSION, "system.user.read").code()).isEqualTo("system.user.read");
        assertThatNullPointerException().isThrownBy(() -> new AccessCheck(null, "x"));
        assertThatNullPointerException().isThrownBy(() -> new AccessResult(AccessKind.RESOURCE, null, true));
    }
}
