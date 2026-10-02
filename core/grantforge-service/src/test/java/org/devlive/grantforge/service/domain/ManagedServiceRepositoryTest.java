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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ManagedServiceRepositoryTest
{
    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private TenantRepository tenants;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            services.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void namesAreUniquePerTenant()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        TenantContext.runInTenant(acme, () -> {
            services.save(ManagedService.create("hive", "zeta", "Zeta", null));
            services.save(ManagedService.create("hive", "alpha", "Alpha", null));
        });
        TenantContext.runInTenant(globex, () -> services.save(ManagedService.create("hive", "alpha", "Alpha", null)));

        assertThat(TenantContext.callInTenant(acme, () -> services.findAllByOrderByNameAsc())).extracting(ManagedService::getName)
                .containsExactly("alpha", "zeta");
        assertThat(TenantContext.callInTenant(acme, () -> services.findByName("zeta"))).isPresent();
        assertThat(TenantContext.callInTenant(globex, () -> services.findByName("zeta"))).isEmpty();
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> services.saveAndFlush(ManagedService.create("hdfs", "alpha",
                "Again", null)))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
