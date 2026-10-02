// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccessEventRepositoryTest
{
    @Autowired
    private AccessEventRepository events;

    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TransactionTemplate transactions;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            events.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private static AccessEvent event(long serviceId, String id, Instant when)
    {
        return AccessEvent.of(serviceId, "a", new AccessEvent.Fields(id, when, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED,
                null, null, Enforcer.GRANTFORGE, null));
    }

    @Test
    void findsKnownEventsAndOldOnes()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long hive = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hive", "hive", "Hive", null)).requireId());
        Instant noon = Instant.parse("2026-10-02T12:00:00Z");
        TenantContext.runInTenant(acme, () -> events.saveAll(List.of(event(hive, "a", noon), event(hive, "b", noon.minusSeconds(60)))));
        assertThat(TenantContext.callInTenant(acme, () -> events.findKnownEventIds(hive, List.of("a", "z")))).containsExactly("a");
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> events.saveAndFlush(event(hive, "a", noon))))
                .isInstanceOf(DataIntegrityViolationException.class);
        List<Long> old = TenantContext.callAsSystem(() -> events.findIdsBefore(noon, PageRequest.of(0, 10)));
        assertThat(old).hasSize(1);
        Integer removed = TenantContext.callAsSystem(() -> transactions.execute(status -> events.removeAll(old)));
        assertThat(removed).isEqualTo(1);
        Integer rest = TenantContext.callInTenant(acme, () -> transactions.execute(status -> events.removeService(hive)));
        assertThat(rest).isEqualTo(1);
    }
}
