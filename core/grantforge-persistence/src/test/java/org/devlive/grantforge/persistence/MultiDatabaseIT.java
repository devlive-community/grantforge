// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.persistence.entity.SampleEntity;
import org.devlive.grantforge.persistence.entity.SampleRepository;
import org.devlive.grantforge.persistence.naming.SchemaNamingVerifier;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.persistence.tenant.TenantSampleEntity;
import org.devlive.grantforge.persistence.tenant.TenantSampleRepository;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the persistence foundation on a real database selected with {@code -Dgrantforge.it.database}.
 *
 * <p>Starting the context already proves that the Liquibase changelog applies and that Hibernate's
 * {@code ddl-auto=validate} accepts the resulting schema; the tests then exercise the behaviours that
 * differ between databases.
 */
@SpringBootTest(classes = TestPersistenceApplication.class, properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/test-changelog.yaml",
        "spring.jpa.hibernate.ddl-auto=validate",
})
class MultiDatabaseIT
{
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();
    // Chinese, German and a 4-byte emoji: fails on non-Unicode columns and 3-byte MySQL utf8.
    private static final String UNICODE_LABEL = "权限管理-ÄÖÜ-🔐";

    @Autowired
    private SampleRepository samples;

    @Autowired
    private TenantSampleRepository tenantSamples;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase()
    {
        DATABASE.close();
    }

    @AfterEach
    void deleteRows()
    {
        samples.deleteAllInBatch();
        TenantContext.callAsSystem(() -> {
            tenantSamples.deleteAllInBatch();
            return null;
        });
    }

    @Test
    void unicodeTextAndMicrosecondTimestampsRoundTrip()
    {
        SampleEntity saved = samples.saveAndFlush(new SampleEntity(UNICODE_LABEL));

        SampleEntity loaded = samples.findById(saved.requireId()).orElseThrow();

        assertThat(loaded.getLabel()).isEqualTo(UNICODE_LABEL);
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void batchedInsertsAndInClausesBeyondOneThousandValuesWork()
    {
        List<SampleEntity> saved = samples.saveAll(
                IntStream.range(0, 2500).mapToObj(i -> new SampleEntity("row-" + i)).toList());
        List<Long> ids = saved.stream().map(SampleEntity::requireId).toList();

        List<SampleEntity> found = InClauseBatcher.query(ids, samples::findAllById);

        assertThat(found).hasSize(2500);
    }

    @Test
    void staleUpdatesAreRejected()
    {
        SampleEntity saved = samples.saveAndFlush(new SampleEntity("a"));
        SampleEntity first = samples.findById(saved.requireId()).orElseThrow();
        SampleEntity second = samples.findById(saved.requireId()).orElseThrow();
        second.setLabel("second");
        samples.saveAndFlush(second);

        first.setLabel("first");
        assertThatThrownBy(() -> samples.saveAndFlush(first)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void tenantsAreIsolated()
    {
        TenantContext.runInTenant(1, () -> tenantSamples.save(new TenantSampleEntity("one")));
        TenantContext.runInTenant(2, () -> tenantSamples.save(new TenantSampleEntity("two")));

        List<String> tenantOne = TenantContext.callInTenant(1,
                () -> tenantSamples.findAll().stream().map(TenantSampleEntity::getLabel).toList());

        assertThat(tenantOne).containsExactly("one");
        assertThat(tenantSamples.findAll()).isEmpty();
    }

    @Test
    void mappedNamesStayPortable()
    {
        assertThat(SchemaNamingVerifier.verify(entityManagerFactory)).isEmpty();
    }
}
