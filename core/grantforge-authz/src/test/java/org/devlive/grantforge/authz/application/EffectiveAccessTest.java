// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EffectiveAccessTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void copiesItsListsAndNeedsItemParts()
    {
        List<EffectiveAccess.Item> resources = new ArrayList<>(List.of(new EffectiveAccess.Item("system", "System", null, ResourceType.MODULE, null)));
        EffectiveAccess access = new EffectiveAccess(List.of(), resources, List.of());
        resources.clear();
        assertThat(access.resources()).hasSize(1);
        assertThatNullPointerException().isThrownBy(() -> new EffectiveAccess.Item("x", "X", null, null, null));
    }
}
