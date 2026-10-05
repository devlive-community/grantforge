// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySourceTest
{
    @Test
    void keepsItsSettingsAndTheLastSync()
    {
        IdentitySource source = IdentitySource.create("corp", IdentitySourceType.LDAP);
        assertThat(source.isEnabled()).isTrue();
        assertThat(source.isProvisioning()).isTrue();
        assertThat(source.getSecret()).isNull();

        source.configure("Corporate", false, false, "{\"a\":1}", 60);
        source.storeSecret("sealed");
        Instant now = Instant.parse("2026-06-01T09:00:00Z");
        source.synced(now, "x".repeat(600));

        assertThat(source.getCode()).isEqualTo("corp");
        assertThat(source.getType()).isEqualTo(IdentitySourceType.LDAP);
        assertThat(source.getName()).isEqualTo("Corporate");
        assertThat(source.isEnabled()).isFalse();
        assertThat(source.isProvisioning()).isFalse();
        assertThat(source.getSettings()).isEqualTo("{\"a\":1}");
        assertThat(source.getSecret()).isEqualTo("sealed");
        assertThat(source.getSyncIntervalMinutes()).isEqualTo(60);
        assertThat(source.getLastSyncedAt()).isEqualTo(now);
        assertThat(source.getLastSyncSummary()).hasSize(512);
        source.synced(now, "short");
        assertThat(source.getLastSyncSummary()).isEqualTo("short");
    }
}
