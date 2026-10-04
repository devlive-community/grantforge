// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserDataAccessTest
{
    @Test
    void findsTheRulesOfAnActionOnAnEntity()
    {
        UserDataAccess.Subject ada = new UserDataAccess.Subject("1", "2", "ada", List.of(), List.of(), List.of(), List.of());
        UserDataAccess.EntityRules read = new UserDataAccess.EntityRules("order", DataAction.READ,
                List.of(new UserDataAccess.Rule(DataScope.SELF, null, List.of())), List.of());
        UserDataAccess access = new UserDataAccess(ada, List.of(read), 3);

        assertThat(access.rules("order", DataAction.READ)).contains(read);
        assertThat(access.rules("order", DataAction.DELETE)).isEmpty();
        assertThat(access.rules("invoice", DataAction.READ)).isEmpty();
    }
}
