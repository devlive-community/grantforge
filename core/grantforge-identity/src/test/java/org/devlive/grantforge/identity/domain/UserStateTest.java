// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserStateTest
{
    @Test
    void namesTheFiltersTheConsoleOffers()
    {
        assertThat(UserState.values()).extracting(Enum::name).containsExactly("ACTIVE", "DISABLED", "LOCKED");
    }
}
