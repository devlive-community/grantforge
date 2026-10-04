// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AccessExplanationTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contracts to test the guards
    void copiesItsListsAndNeedsItsParts()
    {
        List<AccessExplanation.PathRole> roles = new ArrayList<>(List.of(new AccessExplanation.PathRole("editors", "Editors",
                List.of(new Subject(SubjectType.USER, 7, "Alice", "alice")))));
        AccessExplanation.Path path = new AccessExplanation.Path(roles, List.of(new AccessExplanation.PathResource("system.user", "Users",
                ResourceType.PAGE, AccessExplanation.Via.GRANT)));
        roles.clear();
        AccessExplanation explanation = new AccessExplanation(AccessKind.RESOURCE, "system.user", AccessExplanation.Outcome.ALLOWED,
                "Users", List.of(path), List.of(new AccessExplanation.Denial("nobody", "Nobody", "system")));
        assertThat(explanation.paths().get(0).roles()).hasSize(1);
        assertThat(explanation.denials()).hasSize(1);
        assertThat(AccessExplanation.Outcome.values()).hasSize(5);
        assertThat(AccessExplanation.Via.values()).hasSize(4);
        assertThatNullPointerException().isThrownBy(() -> new AccessExplanation(AccessKind.RESOURCE, "x", null, null, List.of(), List.of()));
        assertThatNullPointerException().isThrownBy(() -> new AccessExplanation.PathRole("x", null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new AccessExplanation.PathResource("x", "X", ResourceType.PAGE, null));
        assertThatNullPointerException().isThrownBy(() -> new AccessExplanation.Denial("x", "X", null));
    }
}
