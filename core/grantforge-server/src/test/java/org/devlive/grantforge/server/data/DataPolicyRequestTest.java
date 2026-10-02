// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.DataPolicyCommand;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
class DataPolicyRequestTest
{
    @Test
    void fillsInWhatWasLeftOut()
    {
        DataPolicyCommand command = new DataPolicyRequest("user", DataAction.READ, DataScope.CUSTOM_ORGS, null, null,
                Arrays.asList("12", null)).command();
        assertThat(command.effect()).isEqualTo(GrantEffect.ALLOW);
        assertThat(command.orgUnitIds()).containsExactly(12L);
        DataPolicyCommand bare = new DataPolicyRequest("user", null, null, GrantEffect.DENY, null, null).command();
        assertThat(bare).extracting(DataPolicyCommand::action, DataPolicyCommand::scope, DataPolicyCommand::effect,
                DataPolicyCommand::orgUnitIds).containsExactly(DataAction.READ, DataScope.SELF, GrantEffect.DENY, List.of());
    }
}
