// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataAccessTest
{
    @Test
    void saysNothingAboutEntitiesNoRoleMentions()
    {
        DataSubject subject = new DataSubject(1, 2, "alice", List.of(), List.of(), List.of(), List.of());
        DataAccess.Rules own = new DataAccess.Rules(List.of(DataRule.of(DataScope.SELF)), List.of());
        DataAccess access = new DataAccess(subject, Map.of(new DataAccess.Key("user", DataAction.READ), own));
        assertThat(access.rules("user", DataAction.READ)).isEqualTo(own);
        assertThat(access.rules("user", DataAction.DELETE).allow()).isEmpty();
        assertThat(access.rules("group", DataAction.READ).deny()).isEmpty();
    }
}
