// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.authz.application.AuthorizationSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationResponseTest
{
    private static final AuthorizationSnapshot SNAPSHOT = new AuthorizationSnapshot(1, List.of("b", "a"),
            Set.of("system.user", "system"), Set.of("system.user.read"), Instant.EPOCH);

    @Test
    void sortsTheSnapshotAndFingerprintsIt()
    {
        AuthorizationResponse response = AuthorizationResponse.from(SNAPSHOT, false);

        assertThat(response.roles()).containsExactly("a", "b");
        assertThat(response.resources()).containsExactly("system", "system.user");
        assertThat(response.permissions()).containsExactly("system.user.read");
        assertThat(response.unrestricted()).isFalse();
        assertThat(AuthorizationResponse.from(SNAPSHOT, false).version()).isEqualTo(response.version()).isNotNegative();
        assertThat(AuthorizationResponse.from(SNAPSHOT, true).version()).isNotEqualTo(response.version());
        assertThat(AuthorizationResponse.from(new AuthorizationSnapshot(1, List.of("a", "b"), Set.of("system"),
                Set.of("system.user.read"), Instant.EPOCH), false).version()).isNotEqualTo(response.version());
    }

    @Test
    void theListsAreCopied()
    {
        List<String> resources = new ArrayList<>(List.of("system.user"));
        AuthorizationResponse response = new AuthorizationResponse(1, false, List.of(), resources, List.of());
        resources.add("system.role");

        assertThat(response.resources()).containsExactly("system.user");
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void missingListsAreRejected()
    {
        assertThatThrownBy(() -> new AuthorizationResponse(1, false, List.of(), null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
