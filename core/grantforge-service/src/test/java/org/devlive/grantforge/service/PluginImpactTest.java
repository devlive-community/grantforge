// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.plugin.host.domain.PluginStateRepository;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.domain.ServiceAgent;
import org.devlive.grantforge.service.domain.ServiceAgentRepository;
import org.devlive.grantforge.service.domain.ServicePolicy;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PluginImpact.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PluginImpactTest
{
    @Autowired
    private PluginImpact impact;

    @Autowired
    private PluginRegistry plugins;

    @Autowired
    private PluginStateRepository pluginStates;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private ServicePolicyRepository policies;

    @Autowired
    private ServiceAgentRepository agents;

    private long acme;
    private long globex;

    @BeforeEach
    void createTenants()
    {
        plugins.scan();
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            agents.deleteAllInBatch();
            policies.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        pluginStates.deleteAll();
        tenants.deleteAllInBatch();
        plugins.scan();
    }

    /** A service of a tenant, with as many policies and agents. */
    private long service(long tenant, String type, String name, int policyCount, int agentCount)
    {
        return TenantContext.callInTenant(tenant, () -> {
            long id = services.save(ManagedService.create(type, name, name.toUpperCase(Locale.ROOT), null)).requireId();
            for (int index = 0; index < policyCount; index++) {
                ServicePolicy policy = ServicePolicy.create(id, PolicyType.ACCESS);
                policy.describe("policy-" + index, null, PolicyPriority.NORMAL, true, "[]", "{}");
                policies.save(policy);
            }
            for (int index = 0; index < agentCount; index++) {
                agents.save(ServiceAgent.register(id, "agent-" + index));
            }
            return id;
        });
    }

    @Test
    void listsTheServicesOfEveryTenantThatUseThePluginsTypes()
    {
        long hive = service(acme, "demo", "hive", 2, 1);
        service(acme, "warehouse", "dw", 4, 3);
        long lake = service(globex, "demo", "lake", 0, 0);

        PluginImpact.Report report = impact.ofDisabling("builtin-demo");

        assertThat(report.pluginId()).isEqualTo("builtin-demo");
        assertThat(report.serviceTypes()).containsExactly("demo");
        // Only the services of the plugin's own types, from every tenant, each with what it would leave stranded.
        assertThat(report.services()).extracting(PluginImpact.AffectedService::tenantCode, PluginImpact.AffectedService::serviceId,
                PluginImpact.AffectedService::name, PluginImpact.AffectedService::policies, PluginImpact.AffectedService::agents)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("acme", hive, "hive", 2L, 1L),
                        Tuple.tuple("globex", lake, "lake", 0L, 0L));
    }

    @Test
    void aPluginNobodyUsesOrThatIsOffAlreadyAffectsNothing()
    {
        assertThat(impact.ofDisabling("builtin-demo").services()).isEmpty();
        service(acme, "demo", "hive", 1, 1);
        plugins.setEnabled("builtin-demo", false);
        // Switched off, it provides no service types, so switching it off again changes nothing.
        PluginImpact.Report report = impact.ofDisabling("builtin-demo");
        assertThat(report.serviceTypes()).isEmpty();
        assertThat(report.services()).isEmpty();
    }

    @Test
    void refusesAnUnknownPlugin()
    {
        assertThatThrownBy(() -> impact.ofDisabling("nowhere")).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
