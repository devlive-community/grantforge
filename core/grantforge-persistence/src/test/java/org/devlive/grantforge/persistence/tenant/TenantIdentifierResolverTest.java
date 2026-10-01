// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIdentifierResolverTest
{
    private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

    @Test
    void unboundResolvesToATenantThatOwnsNothing()
    {
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TenantIdentifierResolver.UNBOUND_TENANT);
        assertThat(resolver.isRoot(TenantIdentifierResolver.UNBOUND_TENANT)).isFalse();
    }

    @Test
    void boundTenantIsResolved()
    {
        TenantContext.runInTenant(42, () -> {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(42L);
            assertThat(resolver.isRoot(42L)).isFalse();
        });
    }

    @Test
    void onlySystemContextIsRoot()
    {
        assertThat(TenantContext.callAsSystem(() -> resolver.isRoot(resolver.resolveCurrentTenantIdentifier())))
                .isTrue();
    }

    @Test
    void validatesExistingSessions()
    {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
