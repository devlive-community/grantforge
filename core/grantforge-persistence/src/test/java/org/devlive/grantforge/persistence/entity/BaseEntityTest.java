// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.entity;

import jakarta.persistence.EntityManager;
import org.devlive.grantforge.persistence.id.TsidGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class BaseEntityTest
{
    @Autowired
    private SampleRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistAssignsTsidVersionAndTimestamps()
    {
        Instant before = Instant.now().minusSeconds(1);
        SampleEntity saved = repository.saveAndFlush(new SampleEntity("a"));

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(TsidGenerator.timestampOf(saved.requireId())).isAfter(before);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull().isAfter(before).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void valuesReadBackEqualValuesWritten()
    {
        SampleEntity saved = repository.saveAndFlush(new SampleEntity("a"));
        entityManager.clear();

        SampleEntity loaded = repository.findById(saved.requireId()).orElseThrow();

        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(loaded.getLabel()).isEqualTo("a");
        assertThat(loaded).isEqualTo(saved).hasSameHashCodeAs(saved);
    }

    @Test
    void updateIncrementsVersionAndKeepsCreationTime() throws InterruptedException
    {
        SampleEntity saved = repository.saveAndFlush(new SampleEntity("a"));
        Instant created = saved.getCreatedAt();
        Thread.sleep(5);

        saved.setLabel("b");
        SampleEntity updated = repository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getCreatedAt()).isEqualTo(created);
        assertThat(updated.getUpdatedAt()).isAfter(created);
    }

    @Test
    void staleCopyIsRejected()
    {
        SampleEntity saved = repository.saveAndFlush(new SampleEntity("a"));
        entityManager.clear();
        SampleEntity first = repository.findById(saved.requireId()).orElseThrow();
        entityManager.detach(first);
        SampleEntity second = repository.findById(saved.requireId()).orElseThrow();
        second.setLabel("second");
        repository.saveAndFlush(second);
        entityManager.detach(second);

        first.setLabel("first");
        assertThatThrownBy(() -> repository.saveAndFlush(first)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void newEntitiesAreNotEqualAndKeepTheirHashAcrossPersist()
    {
        SampleEntity a = new SampleEntity("a");
        SampleEntity b = new SampleEntity("b");
        Set<SampleEntity> set = new HashSet<>(Set.of(a));

        assertThat(a).isNotEqualTo(b).isNotEqualTo(null).isNotEqualTo("a").isEqualTo(a);
        assertThat(a.getId()).isNull();
        assertThatIllegalStateException().isThrownBy(a::requireId).withMessageContaining("SampleEntity");

        repository.saveAndFlush(a);

        assertThat(set).contains(a);
        assertThat(a.toString()).isEqualTo("SampleEntity[id=" + a.getId() + "]");
    }
}
