// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class RoleRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleRepository roles;

    private long acme;
    private long globex;

    @BeforeEach
    void createTenants()
    {
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void searchesTheBoundTenantSystemRolesFirst()
    {
        TenantContext.runInTenant(acme, () -> {
            roles.save(Role.create("auditors", "Auditors", null));
            roles.save(Role.create("buyers", "采购", null));
            roles.save(Role.system(SystemRole.TENANT_ADMIN));
        });
        TenantContext.runInTenant(globex, () -> roles.save(Role.create("auditors", "Globex auditors", null)));

        assertThat(TenantContext.callInTenant(acme, () -> roles.search("%"))).extracting(Role::getCode)
                .containsExactly("tenant-admin", "auditors", "buyers");
        assertThat(TenantContext.callInTenant(acme, () -> roles.search("%采购%"))).extracting(Role::getCode).containsExactly("buyers");
        assertThat(TenantContext.callInTenant(acme, () -> roles.search("%audit%"))).hasSize(1);
        assertThat(TenantContext.callInTenant(globex, () -> roles.findByCode("auditors"))).map(Role::getName)
                .contains("Globex auditors");
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> roles.saveAndFlush(Role.create("auditors", "Again", null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
