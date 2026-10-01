// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDeletedTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void namesWhatWasDeleted()
    {
        assertThat(new IdentityDeleted(IdentityDeleted.Kind.GROUP, 7).id()).isEqualTo(7);
        assertThat(IdentityDeleted.Kind.values()).extracting(Enum::name).containsExactly("ACCOUNT", "GROUP", "ORG_UNIT", "POSITION");
        assertThatThrownBy(() -> new IdentityDeleted(null, 7)).isInstanceOf(NullPointerException.class);
    }
}
