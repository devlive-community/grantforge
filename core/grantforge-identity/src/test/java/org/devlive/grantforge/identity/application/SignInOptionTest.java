// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignInOptionTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void needsACodeAndAName()
    {
        assertThat(new SignInOption("okta", "Okta").name()).isEqualTo("Okta");
        assertThatThrownBy(() -> new SignInOption(null, "Okta")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SignInOption("okta", null)).isInstanceOf(NullPointerException.class);
    }
}
