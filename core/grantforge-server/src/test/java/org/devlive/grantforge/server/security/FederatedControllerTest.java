// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FederatedControllerTest
{
    @Test
    void goesOnlyToThisServersAuthorizationOrConsolePages()
    {
        assertThat(FederatedController.target("/oauth2/authorize?client_id=a", "/admin/users")).isEqualTo("/oauth2/authorize?client_id=a");
        assertThat(FederatedController.target("https://evil.example/oauth2/authorize?x", "/admin/users")).isEqualTo("/#/admin/users");
        assertThat(FederatedController.target(null, "//evil.example")).isEqualTo(FederatedController.CONSOLE);
        assertThat(FederatedController.target(null, "admin")).isEqualTo(FederatedController.CONSOLE);
        assertThat(FederatedController.target(null, null)).isEqualTo(FederatedController.CONSOLE);
    }

    @Test
    void keepsTheTargetAcrossTheSecondStep()
    {
        assertThat(FederatedController.secondStep("/oauth2/authorize?client_id=a"))
                .isEqualTo("/#/auth/login?mfa=1&authorize=%2Foauth2%2Fauthorize%3Fclient_id%3Da");
        assertThat(FederatedController.secondStep("/#/admin/users")).isEqualTo("/#/auth/login?mfa=1&redirect=%2Fadmin%2Fusers");
        assertThat(FederatedController.secondStep(FederatedController.CONSOLE)).isEqualTo("/#/auth/login?mfa=1");
    }
}
