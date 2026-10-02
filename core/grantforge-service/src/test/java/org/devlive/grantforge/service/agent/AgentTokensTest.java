// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.service.domain.AgentTokenRepository;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AuditLog.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentTokensTest
{
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Autowired
    private AgentTokenRepository tokens;

    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private AuditLog audit;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long acme;
    private long globex;
    private long hive;

    @BeforeEach
    void createService()
    {
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        hive = TenantContext.callInTenant(acme, () -> services.save(ManagedService.create("hive", "hive", "Hive", null)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            tokens.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        events.deleteAllInBatch();
        tenants.deleteAllInBatch();
    }

    private AgentTokens at(Instant now)
    {
        return new AgentTokens(tokens, services, audit, transactionManager, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokensSignAgentsInUntilRevokedOrExpired()
    {
        AgentTokens agentTokens = at(NOW);
        IssuedToken issued = TenantContext.callInTenant(acme, () -> agentTokens.issue(7, hive, " cluster-a ", null));
        assertThat(issued.secret()).startsWith(AgentTokens.PREFIX).hasSize(47);
        assertThat(issued.toString()).doesNotContain(issued.secret());
        assertThat(issued.token()).extracting(AgentTokenView::name, AgentTokenView::hint, AgentTokenView::usable)
                .containsExactly("cluster-a", issued.secret().substring(0, 10), true);
        // Only the hash is stored.
        assertThat(TenantContext.callAsSystem(() -> tokens.findAll())).allSatisfy(token -> assertThat(token.getTokenHint())
                .isNotEqualTo(issued.secret()));

        assertThat(agentTokens.authenticate(issued.secret())).contains(new AgentCredential(acme, hive, issued.token().id()));
        assertThat(agentTokens.authenticate(issued.secret() + "x")).isEmpty();
        assertThat(agentTokens.authenticate("Bearer nonsense")).isEmpty();
        assertThat(agentTokens.authenticate(AgentTokens.PREFIX + "x".repeat(200))).isEmpty();
        assertThat(TenantContext.callInTenant(acme, () -> agentTokens.list(hive))).singleElement()
                .extracting(AgentTokenView::lastUsedAt).isEqualTo(NOW);

        // Uses are written at most once a minute.
        at(NOW.plusSeconds(30)).authenticate(issued.secret());
        assertThat(TenantContext.callInTenant(acme, () -> agentTokens.list(hive)).get(0).lastUsedAt()).isEqualTo(NOW);
        at(NOW.plusSeconds(90)).authenticate(issued.secret());
        assertThat(TenantContext.callInTenant(acme, () -> agentTokens.list(hive)).get(0).lastUsedAt()).isEqualTo(NOW.plusSeconds(90));

        IssuedToken expiring = TenantContext.callInTenant(acme, () -> agentTokens.issue(7, hive, "short", NOW.plusSeconds(60)));
        assertThat(at(NOW.plusSeconds(59)).authenticate(expiring.secret())).isPresent();
        assertThat(at(NOW.plusSeconds(60)).authenticate(expiring.secret())).isEmpty();

        TenantContext.runInTenant(acme, () -> agentTokens.revoke(7, issued.token().id()));
        TenantContext.runInTenant(acme, () -> agentTokens.revoke(7, issued.token().id()));
        assertThat(agentTokens.authenticate(issued.secret())).isEmpty();
        List<AgentTokenView> listed = TenantContext.callInTenant(acme, () -> agentTokens.list(hive));
        assertThat(listed).extracting(AgentTokenView::name).containsExactly("short", "cluster-a");
        assertThat(listed.get(1).revokedAt()).isEqualTo(NOW);
        assertThat(listed.get(1).usable()).isFalse();
        assertThat(events.findAll()).extracting(event -> event.getAction().name()).containsExactlyInAnyOrder("AGENT_TOKEN_ISSUED",
                "AGENT_TOKEN_ISSUED", "AGENT_TOKEN_REVOKED", "AGENT_TOKEN_REVOKED");
    }

    @Test
    void refusesBadInputAndOtherTenantsServices()
    {
        AgentTokens agentTokens = at(NOW);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> agentTokens.issue(7, hive, " ", NOW)))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST);
                    assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactly("name", "expiresAt");
                });
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> agentTokens.issue(7, hive, "x".repeat(65), null)))
                .isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> TenantContext.callInTenant(globex, () -> agentTokens.issue(7, hive, "a", null)))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> TenantContext.callInTenant(globex, () -> agentTokens.list(hive)))
                .isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> TenantContext.runInTenant(acme, () -> agentTokens.revoke(7, 424242)))
                .isInstanceOf(GrantForgeException.class);
    }

    @Test
    void hashesTokensWithSha256()
    {
        assertThat(AgentTokens.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
