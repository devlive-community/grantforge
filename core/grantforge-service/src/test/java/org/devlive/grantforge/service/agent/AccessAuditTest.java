// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessEventRepository;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.devlive.grantforge.service.domain.Enforcer;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.domain.ServicePolicy;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.jspecify.annotations.Nullable;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccessAuditTest
{
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Autowired
    private AccessEventRepository events;

    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private ServicePolicyRepository policies;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long acme;
    private long globex;
    private long hive;
    private long hdfs;
    private long policy;

    @BeforeEach
    void createServices()
    {
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        hive = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hive", "hive", "Hive", null)).requireId());
        hdfs = TenantContext.callInTenant(globex, () -> services.save(ManagedService.create("hdfs", "hdfs", "HDFS", null)).requireId());
        policy = TenantContext.callInTenant(acme, () -> {
            ServicePolicy sales = ServicePolicy.create(hive, PolicyType.ACCESS);
            sales.describe("sales readers", null, PolicyPriority.NORMAL, true, "[]", "{}");
            return policies.save(sales).requireId();
        });
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            events.deleteAllInBatch();
            policies.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private AccessAudit at(Instant now)
    {
        return new AccessAudit(events, services, policies, transactionManager, Duration.ofDays(30), Clock.fixed(now, ZoneOffset.UTC));
    }

    private AccessEvent.Fields event(String id, Instant when, String user, String resource, AccessOutcome outcome, @Nullable Long decidedBy)
    {
        return new AccessEvent.Fields(id, when, user, "10.0.0.9", resource, "table", "select", "SELECT", outcome, decidedBy, 3L,
                decidedBy == null ? Enforcer.NATIVE : Enforcer.GRANTFORGE, "select * from " + resource);
    }

    @Test
    void storesEachEventOnceAndDropsExpiredOnes()
    {
        AgentCredential agent = new AgentCredential(acme, hive, 9);
        List<AccessEvent.Fields> batch = List.of(event("e1", NOW, "alice", "sales.orders", AccessOutcome.ALLOWED, policy),
                event("e2", NOW.minusSeconds(5), "bob", "hr.people", AccessOutcome.DENIED, null),
                event("e2", NOW.minusSeconds(5), "bob", "hr.people", AccessOutcome.DENIED, null),
                event("old", NOW.minus(Duration.ofDays(31)), "carol", "x", AccessOutcome.ALLOWED, null));
        assertThat(TenantContext.callInTenant(acme, () -> at(NOW).record(agent, "hs2-1", batch))).isEqualTo(new Ingested(2, 1, 1));
        // A batch sent again stores nothing new.
        assertThat(TenantContext.callInTenant(acme, () -> at(NOW).record(agent, "hs2-1", batch))).isEqualTo(new Ingested(0, 3, 1));
        assertThat(TenantContext.callInTenant(acme, () -> at(NOW).record(agent, "hs2-1", List.of()))).isEqualTo(new Ingested(0, 0, 0));
        assertThatThrownBy(() -> at(NOW).record(agent, "hs2-1", Collections.nCopies(1001, batch.get(0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantContext.callInTenant(globex, () -> at(NOW).record(agent, "hs2-1", batch)))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));

        AccessPage page = TenantContext.callInTenant(acme, () -> at(NOW).search(hive, AccessQuery.ALL, 10, null));
        assertThat(page.events()).extracting(view -> view.event().eventId()).containsExactly("e1", "e2");
        assertThat(page.events().get(0)).extracting(AccessEventView::agentInstance, AccessEventView::policyName)
                .containsExactly("hs2-1", "sales readers");
        assertThat(page.events().get(0).event()).isEqualTo(batch.get(0));
        assertThat(page.events().get(1).policyName()).isNull();
        assertThat(page.next()).isNull();
    }

    @Test
    void filtersAndPagesNewestFirst()
    {
        AgentCredential agent = new AgentCredential(acme, hive, 9);
        TenantContext.callInTenant(acme, () -> at(NOW).record(agent, "hs2-1", IntStream.range(0, 7).mapToObj(index -> event("e" + index,
                NOW.minusSeconds(index / 2), index % 2 == 0 ? "Alice" : "bob", "sales.t" + index,
                index < 4 ? AccessOutcome.ALLOWED : AccessOutcome.DENIED, 424242L)).toList()));
        AccessAudit audit = at(NOW);

        AccessPage first = TenantContext.callInTenant(acme, () -> audit.search(hive, AccessQuery.ALL, 3, null));
        assertThat(first.events()).extracting(view -> view.event().eventId()).containsExactly("e1", "e0", "e3");
        // A policy that is gone has no name.
        assertThat(first.events().get(0).policyName()).isNull();
        AccessPage second = TenantContext.callInTenant(acme, () -> audit.search(hive, AccessQuery.ALL, 3, first.next()));
        assertThat(second.events()).extracting(view -> view.event().eventId()).containsExactly("e2", "e5", "e4");
        AccessPage last = TenantContext.callInTenant(acme, () -> audit.search(hive, AccessQuery.ALL, 3, second.next()));
        assertThat(last.events()).extracting(view -> view.event().eventId()).containsExactly("e6");
        assertThat(last.next()).isNull();

        assertThat(TenantContext.callInTenant(acme, () -> audit.search(hive, new AccessQuery(" alice ", "T2", "select",
                AccessOutcome.ALLOWED, NOW.minusSeconds(1), NOW), 50, null)).events()).extracting(view -> view.event().eventId())
                .containsExactly("e2");
        assertThat(TenantContext.callInTenant(acme, () -> audit.search(hive, new AccessQuery("%", "_", "update", null, null, null), 50,
                " ")).events()).isEmpty();
        assertThat(TenantContext.callInTenant(acme, () -> audit.search(hive, new AccessQuery(null, null, null, AccessOutcome.DENIED, null,
                null), 0, null)).events()).hasSize(1);
        assertThat(TenantContext.callInTenant(globex, () -> audit.search(hdfs, AccessQuery.ALL, 50, null)).events()).isEmpty();
        assertThatThrownBy(() -> TenantContext.callInTenant(globex, () -> audit.search(hive, AccessQuery.ALL, 50, null)))
                .isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> audit.search(hive, AccessQuery.ALL, 50, "not-a-cursor")))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void purgesEventsPastTheRetentionPeriodInEveryTenant()
    {
        TenantContext.callInTenant(acme, () -> at(NOW).record(new AgentCredential(acme, hive, 9), "a", List.of(
                event("old", NOW.minus(Duration.ofDays(20)), "alice", "x", AccessOutcome.ALLOWED, null),
                event("new", NOW, "alice", "x", AccessOutcome.ALLOWED, null))));
        TenantContext.callInTenant(globex, () -> at(NOW).record(new AgentCredential(globex, hdfs, 9), "b", List.of(
                event("old", NOW.minus(Duration.ofDays(20)), "dave", "/data", AccessOutcome.DENIED, null))));
        assertThat(at(NOW.plus(Duration.ofDays(15))).purge()).isEqualTo(2);
        assertThat(at(NOW.plus(Duration.ofDays(15))).purge()).isZero();
        assertThat(TenantContext.callAsSystem(() -> events.findAll())).extracting(AccessEvent::getEventId).containsExactly("new");
    }

    @Test
    void theRetentionPeriodMustBePositive()
    {
        assertThatThrownBy(() -> new AccessAudit(events, services, policies, transactionManager, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
