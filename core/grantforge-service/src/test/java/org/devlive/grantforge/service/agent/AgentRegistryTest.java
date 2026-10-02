// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServiceAgentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentRegistryTest
{
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Autowired
    private ServiceAgentRepository agents;

    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long acme;
    private long hive;

    @BeforeEach
    void createService()
    {
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        hive = TenantContext.callInTenant(acme, () -> {
            ManagedService service = ManagedService.create("hive", "hive", "Hive", null);
            service.policiesChanged();
            service.policiesChanged();
            return services.save(service).requireId();
        });
    }

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

    private AgentRegistry at(Instant now)
    {
        return new AgentRegistry(agents, services, transactionManager, Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void heartbeatsRegisterAgentsAndTellThemThePolicyVersion()
    {
        AgentCredential credential = new AgentCredential(acme, hive, 5);
        Heartbeat first = TenantContext.callInTenant(acme, () -> at(NOW).heartbeat(credential,
                new AgentReport("hs2-1", "10.0.0.5", "1.0.0", null), "10.0.0.5"));
        assertThat(first).isEqualTo(new Heartbeat(2, 30));
        TenantContext.callInTenant(acme, () -> at(NOW).heartbeat(credential, new AgentReport("hs2-2", null, null, 2L), null));

        assertThat(TenantContext.callInTenant(acme, () -> at(NOW).list(hive))).extracting(AgentView::instance, AgentView::status)
                .containsExactly(tuple("hs2-1", AgentStatus.OUTDATED),
                        tuple("hs2-2", AgentStatus.CURRENT));
        TenantContext.callInTenant(acme, () -> at(NOW.plusSeconds(60)).heartbeat(credential,
                new AgentReport("hs2-1", "10.0.0.6", "1.0.1", 2L), "10.0.0.6"));
        AgentView moved = TenantContext.callInTenant(acme, () -> at(NOW.plusSeconds(60)).list(hive)).get(0);
        assertThat(moved).extracting(AgentView::host, AgentView::agentVersion, AgentView::appliedPolicyVersion, AgentView::clientIp,
                AgentView::lastSeenAt, AgentView::status).containsExactly("10.0.0.6", "1.0.1", 2L, "10.0.0.6", NOW.plusSeconds(60),
                AgentStatus.CURRENT);
        // Three missed heartbeats make an agent silent.
        assertThat(TenantContext.callInTenant(acme, () -> at(NOW.plusSeconds(91)).list(hive)).get(1).status()).isEqualTo(AgentStatus.SILENT);

        TenantContext.runInTenant(acme, () -> at(NOW).forget(hive, moved.id()));
        assertThat(TenantContext.callInTenant(acme, () -> at(NOW).list(hive))).extracting(AgentView::instance).containsExactly("hs2-2");
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> at(NOW).forget(hive, moved.id()))).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> at(NOW).list(424242))).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> at(NOW).heartbeat(new AgentCredential(acme, 424242, 5),
                new AgentReport("x", null, null, null), null))).isInstanceOf(GrantForgeException.class);
    }

    @Test
    void theRefreshIntervalMustBePositive()
    {
        assertThatThrownBy(() -> new AgentRegistry(agents, services, transactionManager, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
