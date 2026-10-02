// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.persistence.tenant.TenantSampleEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RowScopesTest
{
    @Autowired
    private ScopedSamples samples;

    @Autowired
    private RowScopes registered;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            samples.deleteAllInBatch();
            return null;
        });
    }

    @Test
    void withoutDataPoliciesEveryRowIsInScope()
    {
        assertThat(registered).isSameAs(RowScopes.unrestricted());
        TenantContext.runInTenant(1L, () -> {
            long kept = samples.save(new TenantSampleEntity("kept")).requireId();
            assertThat(registered.requireWithin(7L, samples, TenantSampleEntity.class, DataAction.DELETE, kept).getLabel())
                    .isEqualTo("kept");
            assertThatThrownBy(() -> registered.requireWithin(7L, samples, TenantSampleEntity.class, DataAction.READ, 424242L))
                    .isInstanceOfSatisfying(GrantForgeException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND))
                    .hasMessageContaining("TenantSampleEntity 424242");
        });
    }

    @Test
    void rowsOutsideAScopeLookAsIfTheyDidNotExist()
    {
        RowScopes keptOnly = new RowScopes()
        {
            @Override
            public <T> Specification<T> scope(long accountId, Class<T> type, DataAction action)
            {
                return (root, query, builder) -> builder.equal(root.get("label"), "kept");
            }
        };
        TenantContext.runInTenant(1L, () -> {
            long kept = samples.save(new TenantSampleEntity("kept")).requireId();
            long hidden = samples.save(new TenantSampleEntity("hidden")).requireId();
            assertThat(keptOnly.requireWithin(7L, samples, TenantSampleEntity.class, DataAction.UPDATE, kept).requireId())
                    .isEqualTo(kept);
            assertThatThrownBy(() -> keptOnly.requireWithin(7L, samples, TenantSampleEntity.class, DataAction.UPDATE, hidden))
                    .isInstanceOf(GrantForgeException.class);
        });
    }
}
