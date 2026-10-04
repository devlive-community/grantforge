// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.EffectiveAccess;
import org.devlive.grantforge.authz.application.SimulationResult;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationResponseTest
{
    @Test
    void convertsTheDifferences()
    {
        EffectiveAccess.Item page = new EffectiveAccess.Item("system.user", "Users", "permissionNames.users", ResourceType.PAGE, "system");
        EffectiveAccess.Item api = new EffectiveAccess.Item("system.user.read", "Read", null, ResourceType.API, "api");
        SimulationResponse response = SimulationResponse.from(7, new SimulationResult(List.of("a"), List.of("a", "b"), List.of(page),
                List.of(), List.of(), List.of(api)));
        assertThat(response.accountId()).isEqualTo("7");
        assertThat(response.rolesAfter()).containsExactly("a", "b");
        assertThat(response.gainedResources()).extracting(EffectiveAccessResponse.Item::nameKey).containsExactly("permissionNames.users");
        assertThat(response.lostPermissions()).extracting(EffectiveAccessResponse.Item::code).containsExactly("system.user.read");
        assertThat(response.lostResources()).isEmpty();
        assertThat(response.gainedPermissions()).isEmpty();
    }
}
