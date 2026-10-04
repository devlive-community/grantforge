// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationAccessTest
{
    @Test
    void copiesItsParts()
    {
        DataSubject subject = new DataSubject(1, 2, "ada", List.of(3L), List.of("/3/"), List.of(), List.of());
        ApplicationAccess access = new ApplicationAccess(subject, List.of(3L, 4L), Map.of(new DataAccess.Key("order", DataAction.READ),
                new DataAccess.Rules(List.of(DataRule.of(DataScope.SELF)), List.of())));

        assertThat(access.orgUnitsAndBelow()).containsExactly(3L, 4L);
        assertThat(access.rules()).hasSize(1);
    }
}
