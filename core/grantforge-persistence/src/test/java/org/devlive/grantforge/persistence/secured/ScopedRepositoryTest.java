// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.persistence.tenant.TenantSampleEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScopedRepositoryTest
{
    @Autowired
    private ScopedSamples samples;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            samples.deleteAllInBatch();
            return null;
        });
    }

    @Test
    void findsRowsOnlyInsideTheScope()
    {
        TenantContext.runInTenant(1L, () -> {
            long kept = samples.save(new TenantSampleEntity("kept")).requireId();
            long hidden = samples.save(new TenantSampleEntity("hidden")).requireId();
            Specification<TenantSampleEntity> scope = (root, query, builder) -> builder.equal(root.get("label"), "kept");
            assertThat(samples.findWithin(kept, scope)).map(TenantSampleEntity::getLabel).contains("kept");
            assertThat(samples.findWithin(hidden, scope)).isEmpty();
            assertThat(samples.existsWithin(kept, scope)).isTrue();
            assertThat(samples.existsWithin(hidden, scope)).isFalse();
            assertThat(samples.existsWithin(424242L, scope)).isFalse();
        });
    }

    @Test
    void findsTheRowsAmongIdsInsideTheScope()
    {
        TenantContext.runInTenant(1L, () -> {
            long kept = samples.save(new TenantSampleEntity("kept")).requireId();
            long hidden = samples.save(new TenantSampleEntity("hidden")).requireId();
            Specification<TenantSampleEntity> scope = (root, query, builder) -> builder.equal(root.get("label"), "kept");
            assertThat(samples.findAllWithin(List.of(kept, hidden, 424242L), scope)).extracting(TenantSampleEntity::requireId)
                    .containsExactly(kept);
            assertThat(samples.findAllWithin(List.of(), scope)).isEmpty();
        });
    }
}
