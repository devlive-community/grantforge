// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationSnapshotTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsSetsAndAnswersPermissionQuestions()
    {
        Set<String> permissions = new HashSet<>(Set.of("system.user.read"));
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(1, List.of("auditors"), Set.of("system.user"), permissions,
                Instant.EPOCH);
        permissions.add("system.user.delete");

        assertThat(snapshot.holds("system.user.read")).isTrue();
        assertThat(snapshot.holds("system.user.delete")).isFalse();
        assertThatThrownBy(() -> new AuthorizationSnapshot(1, List.of(), Set.of(), Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
