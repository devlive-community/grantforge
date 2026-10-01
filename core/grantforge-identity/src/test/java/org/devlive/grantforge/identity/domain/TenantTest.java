// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantTest
{
    @Test
    void createNormalizesAndAssignsTheIdImmediately()
    {
        Tenant tenant = Tenant.create(" Acme-1 ", "  Acme Corp ");

        assertThat(tenant.getCode()).isEqualTo("acme-1");
        assertThat(tenant.getName()).isEqualTo("Acme Corp");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getAuthzVersion()).isZero();
        assertThat(tenant.getId()).isNotNull();
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void invalidCodesAndNamesAreRejected()
    {
        assertThatThrownBy(() -> Tenant.create("1abc", "n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("a", "n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("a_b", "n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("a".repeat(65), "n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create(null, "n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("ab", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("ab", "x".repeat(Tenant.NAME_MAX + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Tenant.create("a".repeat(64), "x".repeat(Tenant.NAME_MAX)).getCode()).hasSize(64);
    }

    @Test
    void renamingValidatesTheName()
    {
        Tenant tenant = Tenant.create("acme", "Acme");

        tenant.rename("  Acme Group ");
        assertThat(tenant.getName()).isEqualTo("Acme Group");
        assertThatThrownBy(() -> tenant.rename(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tenant.getName()).isEqualTo("Acme Group");
    }

    @Test
    void thePlatformTenantCannotBeSuspended()
    {
        Tenant platform = Tenant.create("default", "Default").markPlatform();

        assertThat(platform.isPlatform()).isTrue();
        assertThat(Tenant.create("acme", "Acme").isPlatform()).isFalse();
        assertThatThrownBy(platform::suspend).isInstanceOf(IllegalStateException.class);
        assertThat(platform.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void suspensionIsReversible()
    {
        Tenant tenant = Tenant.create("acme", "Acme");

        tenant.suspend();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        tenant.activate();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }
}
