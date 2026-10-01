// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest
{
    @Test
    void unboundByDefault()
    {
        assertThat(TenantContext.currentTenantId()).isEmpty();
        assertThat(TenantContext.isSystem()).isFalse();
        assertThatIllegalStateException().isThrownBy(TenantContext::requireTenantId);
    }

    @Test
    void bindsOnlyDuringTheCallback()
    {
        long seen = TenantContext.callInTenant(7, TenantContext::requireTenantId);

        assertThat(seen).isEqualTo(7);
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void nestedBindingsRestoreTheOuterOne()
    {
        TenantContext.runInTenant(1, () -> {
            OptionalLong inner = TenantContext.callInTenant(2, TenantContext::currentTenantId);
            boolean innerSystem = TenantContext.callAsSystem(TenantContext::isSystem);

            assertThat(inner).hasValue(2);
            assertThat(innerSystem).isTrue();
            assertThat(TenantContext.currentTenantId()).hasValue(1);
            assertThat(TenantContext.isSystem()).isFalse();
        });
    }

    @Test
    void exceptionsRestoreTheBinding()
    {
        assertThatThrownBy(() -> TenantContext.runInTenant(3, () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void systemContextHasNoTenant()
    {
        TenantContext.callAsSystem(() -> {
            assertThat(TenantContext.isSystem()).isTrue();
            assertThat(TenantContext.currentTenantId()).isEmpty();
            assertThatIllegalStateException().isThrownBy(TenantContext::requireTenantId);
            return null;
        });
    }

    @Test
    void bindingIsThreadLocal() throws Exception
    {
        OptionalLong otherThread = TenantContext.callInTenant(9,
                () -> CompletableFuture.supplyAsync(TenantContext::currentTenantId).join());

        assertThat(otherThread).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveTenants(long tenantId)
    {
        assertThatIllegalArgumentException().isThrownBy(() -> TenantContext.callInTenant(tenantId, () -> 1));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contracts to test the guards
    void rejectsNullActions()
    {
        assertThatNullPointerException().isThrownBy(() -> TenantContext.callInTenant(1, null));
        assertThatNullPointerException().isThrownBy(() -> TenantContext.runInTenant(1, null));
        assertThatNullPointerException().isThrownBy(() -> TenantContext.callAsSystem(null));
    }
}
