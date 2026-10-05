// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import org.devlive.grantforge.identity.application.SignInOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapResponseTest
{
    @Test
    void exposesItsComponents()
    {
        List<SignInOption> options = new ArrayList<>(List.of(new SignInOption("okta", "Okta")));
        BootstrapResponse response = new BootstrapResponse(true, false, options);
        options.clear();

        assertThat(response.setupRequired()).isTrue();
        assertThat(response.registrationEnabled()).isFalse();
        assertThat(response.signInSources()).containsExactly(new SignInOption("okta", "Okta"));
    }
}
