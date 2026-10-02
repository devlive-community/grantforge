// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.PolicyType;
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
class ServicePolicyRepositoryTest
{
    @Autowired
    private ServicePolicyRepository policies;

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
            policies.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private static ServicePolicy policy(long serviceId, PolicyType type, String name)
    {
        ServicePolicy policy = ServicePolicy.create(serviceId, type);
        policy.describe(name, null, PolicyPriority.NORMAL, true, "[]", "{}");
        return policy;
    }

    @Test
    void listsPoliciesPerServiceAndKindWithNamesUniquePerService()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        long hive = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hive", "hive", "Hive", null)).requireId());
        long hdfs = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hdfs", "hdfs", "HDFS", null)).requireId());
        TenantContext.runInTenant(acme, () -> {
            policies.save(policy(hive, PolicyType.ACCESS, "zeta"));
            policies.save(policy(hive, PolicyType.ACCESS, "alpha"));
            policies.save(policy(hive, PolicyType.DATA_MASK, "mask"));
            policies.save(policy(hdfs, PolicyType.ACCESS, "alpha"));
        });

        assertThat(TenantContext.callInTenant(acme, () -> policies.findByServiceIdAndPolicyTypeOrderByNameAsc(hive, PolicyType.ACCESS)))
                .extracting(ServicePolicy::getName).containsExactly("alpha", "zeta");
        assertThat(TenantContext.callInTenant(acme, () -> policies.findByServiceIdAndName(hive, "mask"))).isPresent();
        assertThat(TenantContext.callInTenant(globex, () -> policies.findByServiceIdAndName(hive, "mask"))).isEmpty();
        assertThat(TenantContext.callInTenant(acme, () -> policies.countByServiceId(hive))).isEqualTo(3);
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> policies.saveAndFlush(policy(hive, PolicyType.ROW_FILTER, "zeta"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        Integer removed = TenantContext.callInTenant(acme, () -> transactions.execute(status -> policies.removeService(hive)));
        assertThat(removed).isEqualTo(3);
        assertThat(TenantContext.callInTenant(acme, () -> policies.countByServiceId(hdfs))).isEqualTo(1);
    }
}
