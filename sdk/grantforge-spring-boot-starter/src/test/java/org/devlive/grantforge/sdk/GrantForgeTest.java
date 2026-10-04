// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrantForgeTest
{
    private final GrantForgeClient client = mock(GrantForgeClient.class);

    @Test
    void checksTheCurrentUser()
    {
        when(client.authorization("t")).thenReturn(SdkTestData.ada());
        GrantForge grantForge = new GrantForge(client, () -> "t");

        assertThat(grantForge.current().username()).isEqualTo("ada");
        assertThat(grantForge.hasPermission("orders.read")).isTrue();
        assertThat(grantForge.hasResource("shop.orders")).isTrue();
        grantForge.require("orders.read");
        assertThatThrownBy(() -> grantForge.require("orders.read", "orders.delete")).isInstanceOfSatisfying(GrantForgeException.class,
                refused -> {
                    assertThat(refused.getReason()).isEqualTo(Reason.FORBIDDEN);
                    assertThat(refused).hasMessageContaining("orders.delete");
                });
    }

    @Test
    void refusesRequestsWithoutAToken()
    {
        assertThatThrownBy(() -> new GrantForge(client, () -> null).current()).isInstanceOfSatisfying(GrantForgeException.class,
                refused -> assertThat(refused.getReason()).isEqualTo(Reason.UNAUTHENTICATED));
    }
}
