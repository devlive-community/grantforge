// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.persistence.naming.SchemaNamingVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hibernate binds the tenant when a session opens, so each tenant's work runs in its own transaction
 * started inside the binding, exactly as request handling does in the application.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantScopedEntityTest
{
    @Autowired
    private TenantSampleRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private <T> T inTransaction(Supplier<T> work)
    {
        T result = new TransactionTemplate(transactionManager).execute(status -> work.get());
        assertThat(result).isNotNull();
        return result;
    }

    private TenantSampleEntity saveIn(long tenantId, String label)
    {
        return TenantContext.callInTenant(tenantId, () -> inTransaction(() -> repository.save(new TenantSampleEntity(label))));
    }

    private List<String> labelsIn(long tenantId)
    {
        return TenantContext.callInTenant(tenantId,
                () -> inTransaction(() -> repository.findAll().stream().map(TenantSampleEntity::getLabel).toList()));
    }

    @AfterEach
    void deleteEverything()
    {
        TenantContext.callAsSystem(() -> inTransaction(() -> {
            repository.deleteAllInBatch();
            return Boolean.TRUE;
        }));
    }

    @Test
    void eachTenantSeesOnlyItsOwnRows()
    {
        TenantSampleEntity first = saveIn(1, "first");
        TenantSampleEntity second = saveIn(2, "second");

        assertThat(first.getTenantId()).isEqualTo(1L);
        assertThat(second.getTenantId()).isEqualTo(2L);
        assertThat(labelsIn(1)).containsExactly("first");
        assertThat(labelsIn(2)).containsExactly("second");
        assertThat(TenantContext.callInTenant(1, () -> inTransaction(() -> repository.findById(second.requireId()))))
                .isEmpty();
    }

    @Test
    void unboundContextReadsNothingAndCannotInsert()
    {
        saveIn(1, "first");

        assertThat(inTransaction(() -> repository.findAll())).isEmpty();
        assertThatThrownBy(() -> inTransaction(() -> repository.save(new TenantSampleEntity("orphan"))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("no tenant is bound");
    }

    @Test
    void systemContextSeesEveryTenantButCannotInsert()
    {
        saveIn(1, "first");
        saveIn(2, "second");

        List<String> all = TenantContext.callAsSystem(
                () -> inTransaction(() -> repository.findAll().stream().map(TenantSampleEntity::getLabel).toList()));

        assertThat(all).containsExactlyInAnyOrder("first", "second");
        assertThatThrownBy(() -> TenantContext.callAsSystem(
                () -> inTransaction(() -> repository.save(new TenantSampleEntity("system")))))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void mappedNamesStayPortable()
    {
        assertThat(SchemaNamingVerifier.verify(entityManagerFactory)).isEmpty();
        assertThat(SchemaNamingVerifier.mappedNames(entityManagerFactory).columns()).contains("tenant_id");
    }
}
