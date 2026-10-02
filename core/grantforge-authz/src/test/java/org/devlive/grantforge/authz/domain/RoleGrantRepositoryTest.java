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
class RoleGrantRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleGrantRepository grants;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long acme;
    private long globex;
    private Resource page;

    @BeforeEach
    void createRows()
    {
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        long app = applications.save(Application.create("console", "Console", null)).requireId();
        page = resources.save(Resource.create(app, null, ResourceType.PAGE, "users", CatalogTestData.details("Users"), 0));
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            grants.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
        tenants.deleteAllInBatch();
    }

    private long grantIn(long tenant, String role)
    {
        return TenantContext.callInTenant(tenant, () -> {
            long id = roles.save(Role.create(role, role, null)).requireId();
            grants.save(RoleGrant.create(id, page, GrantEffect.ALLOW, null, 1));
            return id;
        });
    }

    @Test
    void findsGrantsByRoleAndCountsThemAcrossTenants()
    {
        long auditors = grantIn(acme, "auditors");
        long buyers = grantIn(acme, "buyers");
        grantIn(globex, "auditors");

        assertThat(TenantContext.callInTenant(acme, () -> grants.findByRoleId(auditors))).hasSize(1);
        assertThat(TenantContext.callInTenant(acme, () -> grants.findByRoleIdIn(List.of(auditors, buyers)))).hasSize(2);
        assertThat(TenantContext.callInTenant(acme, () -> grants.countByResourceId(page.requireId()))).isEqualTo(2);
        assertThat(TenantContext.callAsSystem(() -> grants.countByResourceId(page.requireId()))).isEqualTo(3);
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> grants.saveAndFlush(RoleGrant.create(auditors, page,
                GrantEffect.DENY, null, 1)))).isInstanceOf(DataIntegrityViolationException.class);
        Integer removed = TenantContext.callInTenant(acme, () -> new TransactionTemplate(transactionManager).execute(status ->
                grants.removeRole(auditors)));
        assertThat(removed).isOne();
    }
}
