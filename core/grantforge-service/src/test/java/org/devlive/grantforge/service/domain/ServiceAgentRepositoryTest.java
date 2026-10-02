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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ServiceAgentRepositoryTest
{
    @Autowired
    private ServiceAgentRepository agents;

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
            agents.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private static ServiceAgent agent(long serviceId, String instance)
    {
        ServiceAgent agent = ServiceAgent.register(serviceId, instance);
        agent.heartbeat(1, null, null, null, null, Instant.parse("2026-10-02T12:00:00Z"));
        return agent;
    }

    @Test
    void keepsOneRowPerAgentOfAService()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long hive = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hive", "hive", "Hive", null)).requireId());
        TenantContext.runInTenant(acme, () -> {
            agents.save(agent(hive, "zz"));
            agents.save(agent(hive, "aa"));
        });
        assertThat(TenantContext.callInTenant(acme, () -> agents.findByServiceIdOrderByInstanceAsc(hive))).extracting(ServiceAgent::getInstance)
                .containsExactly("aa", "zz");
        assertThat(TenantContext.callInTenant(acme, () -> agents.findByServiceIdAndInstance(hive, "zz"))).isPresent();
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> agents.saveAndFlush(agent(hive, "aa"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        Integer removed = TenantContext.callInTenant(acme, () -> transactions.execute(status -> agents.removeService(hive)));
        assertThat(removed).isEqualTo(2);
    }
}
