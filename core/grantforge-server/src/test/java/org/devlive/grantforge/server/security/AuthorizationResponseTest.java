// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.authz.application.AuthorizationSnapshot;
import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        AuthorizationResponse response = AuthorizationResponse.from(SNAPSHOT, false, Map.of());

        assertThat(response.roles()).containsExactly("a", "b");
        assertThat(response.resources()).containsExactly("system", "system.user");
        assertThat(response.permissions()).containsExactly("system.user.read");
        assertThat(response.unrestricted()).isFalse();
        assertThat(AuthorizationResponse.from(SNAPSHOT, false, Map.of()).version()).isEqualTo(response.version()).isNotNegative();
        assertThat(AuthorizationResponse.from(SNAPSHOT, true, Map.of()).version()).isEqualTo(response.version());
        assertThat(AuthorizationResponse.versionOf(SNAPSHOT, Map.of())).isEqualTo(response.version());
        assertThat(AuthorizationResponse.from(new AuthorizationSnapshot(1, List.of("a", "b"), Set.of("system"),
                Set.of("system.user.read"), Instant.EPOCH), false, Map.of()).version()).isNotEqualTo(response.version());
    }

    @Test
    void listsTheRestrictedFieldsAndFingerprintsThemToo()
    {
        Map<String, FieldMode> fields = Map.of("user.email", new FieldMode(FieldView.masked(MaskStrategy.EMAIL), FieldWriteMode.READONLY));
        AuthorizationResponse response = AuthorizationResponse.from(SNAPSHOT, false, fields);

        assertThat(response.fields()).containsExactly(Map.entry("user.email",
                new FieldModeResponse(FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.READONLY)));
        assertThat(response.version()).isEqualTo(AuthorizationResponse.versionOf(SNAPSHOT, fields))
                .isNotEqualTo(AuthorizationResponse.versionOf(SNAPSHOT, Map.of()));
    }

    @Test
    void theListsAreCopied()
    {
        List<String> resources = new ArrayList<>(List.of("system.user"));
        AuthorizationResponse response = new AuthorizationResponse(1, false, List.of(), resources, List.of(), Map.of());
        resources.add("system.role");

        assertThat(response.resources()).containsExactly("system.user");
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void missingListsAreRejected()
    {
        assertThatThrownBy(() -> new AuthorizationResponse(1, false, List.of(), null, List.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
