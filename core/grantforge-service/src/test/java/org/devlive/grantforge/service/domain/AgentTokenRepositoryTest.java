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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentTokenRepositoryTest
{
    @Autowired
    private AgentTokenRepository tokens;

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
            tokens.deleteAllInBatch();
            agents.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void findsTokensByHashAcrossTenantsAndRemovesThemWithTheirService()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long hive = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hive", "hive", "Hive", null)).requireId());
        TenantContext.runInTenant(acme, () -> {
            tokens.save(AgentToken.create(hive, "a", "h1", "gfa_1", null));
            tokens.save(AgentToken.create(hive, "b", "h2", "gfa_2", null));
        });
        assertThat(TenantContext.callAsSystem(() -> tokens.findByTokenHash("h2"))).map(AgentToken::getName).contains("b");
        assertThat(TenantContext.callInTenant(acme, () -> tokens.findByServiceIdOrderByCreatedAtDescIdDesc(hive))).hasSize(2);
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> tokens.saveAndFlush(AgentToken.create(hive, "c", "h1", "gfa_3", null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        Integer removed = TenantContext.callInTenant(acme, () -> transactions.execute(status -> tokens.removeService(hive)));
        assertThat(removed).isEqualTo(2);
    }
}
