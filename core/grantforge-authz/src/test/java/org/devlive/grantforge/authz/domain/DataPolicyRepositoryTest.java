// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataPolicyRepositoryTest
{
    @Autowired
    private DataPolicyRepository policies;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TransactionTemplate transactions;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private static DataPolicy policy(long roleId, String entity)
    {
        DataPolicy policy = DataPolicy.create(roleId, entity);
        policy.describe(DataAction.READ, DataScope.TENANT, GrantEffect.ALLOW, null, null);
        return policy;
    }

    @Test
    void listsAndRemovesThePoliciesOfARole()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long role = TenantContext.callInTenant(acme, () -> roles.save(Role.create("auditors", "Auditors", null)).requireId());
        TenantContext.runInTenant(acme, () -> {
            policies.save(policy(role, "user"));
            policies.save(policy(role, "group"));
        });
        assertThat(TenantContext.callInTenant(acme, () -> policies.findByRoleIdOrderByEntityCodeAscIdAsc(role)))
                .extracting(DataPolicy::getEntityCode).containsExactly("group", "user");
        Integer removed = TenantContext.callInTenant(acme, () -> transactions.execute(status -> policies.removeRole(role)));
        assertThat(removed).isEqualTo(2);
    }
}
