// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthorizationTest
{
    @Test
    void answersAboutPermissionsResourcesAndRoles()
    {
        UserAuthorization ada = SdkTestData.ada();

        assertThat(ada.hasPermission("orders.read")).isTrue();
        assertThat(ada.hasPermission("orders.delete")).isFalse();
        assertThat(ada.hasResource("shop.orders")).isTrue();
        assertThat(ada.hasResource("shop.admin")).isFalse();
        assertThat(ada.hasRole("sellers")).isTrue();
        assertThat(ada.hasRole("admins")).isFalse();
    }
}
