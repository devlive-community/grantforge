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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleParentRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleParentRepository parents;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
            parents.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private long role(long tenant, String code)
    {
        return TenantContext.callInTenant(tenant, () -> roles.save(Role.create(code, code, null)).requireId());
    }

    @Test
    void keepsLinksPerTenantOnceAndRemovesThemWithTheirRole()
    {
        long base = role(acme, "base");
        long viewers = role(acme, "viewers");
        long editors = role(acme, "editors");
        long foreign = role(globex, "base");
        TenantContext.runInTenant(acme, () -> parents.saveAll(List.of(RoleParent.of(viewers, base),
                RoleParent.of(editors, viewers))));
        TenantContext.runInTenant(globex, () -> parents.save(RoleParent.of(foreign, role(globex, "other"))));

        assertThat(TenantContext.callInTenant(acme, () -> parents.findAll())).hasSize(2);
        assertThat(TenantContext.callInTenant(acme, () -> parents.findByRoleId(editors))).extracting(RoleParent::getParentId)
                .containsExactly(viewers);
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> parents.saveAndFlush(RoleParent.of(viewers, base))))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Removing a role takes the links from and to it along.
        TenantContext.runInTenant(acme, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                parents.removeRole(viewers)));
        assertThat(TenantContext.callInTenant(acme, () -> parents.findAll())).isEmpty();
        assertThat(TenantContext.callInTenant(globex, () -> parents.findAll())).hasSize(1);
    }
}
