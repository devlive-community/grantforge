// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.ConsoleSessionRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.TenantStatus;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@RecordApplicationEvents
@Import({AuditLog.class, IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class,
        ConsoleSessionService.class, TenantService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantServiceTest
{
    @Autowired
    private ApplicationEvents published;

    private static final String PASSWORD = "a long enough password";

    @Autowired
    private TenantService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private ConsoleSessionRepository sessions;

    @Autowired
    private ConsoleSessionService consoleSessions;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private SessionTerminator terminator;

    private long platform;
    private long root;
    private long ordinary;

    @BeforeEach
    void createPlatform()
    {
        ((RecordingSessionTerminator) terminator).clear();
        platform = tenants.save(Tenant.create("default", "Platform").markPlatform()).requireId();
        root = TenantContext.callInTenant(platform, () -> accounts.save(
                UserAccount.create("root", "h", Instant.EPOCH).markSystemAccount()).requireId());
        ordinary = TenantContext.callInTenant(platform, () -> accounts.save(
                UserAccount.create("plain", "h", Instant.EPOCH)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            sessions.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private <T> T asPlatform(Supplier<T> action)
    {
        return TenantContext.callInTenant(platform, action);
    }

    private static ErrorCode codeOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    private TenantSummary createAcme()
    {
        return asPlatform(() -> service.create(root, new TenantCommand("Acme", " Acme Corp ", "boss", "The Boss", PASSWORD)));
    }

    @Test
    void onlySystemAccountsOfThePlatformTenantAdministerThePlatform()
    {
        TenantSummary acme = createAcme();
        long boss = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("boss")).orElseThrow().requireId();

        assertThat(published.stream(TenantCreated.class)).containsExactly(new TenantCreated(acme.id(), false));
        assertThat(asPlatform(() -> service.isPlatformAdministrator(root))).isTrue();
        assertThat(asPlatform(() -> service.isPlatformAdministrator(ordinary))).isFalse();
        // The new tenant's own administrator manages only that tenant.
        assertThat(TenantContext.callInTenant(acme.id(), () -> service.isPlatformAdministrator(boss))).isFalse();
        assertThatThrownBy(() -> asPlatform(() -> service.list(ordinary, null, new PageQuery(1, 10))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> TenantContext.callInTenant(acme.id(), () -> service.find(boss, platform)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void createsATenantWithAnAdministratorWhoMustChangeThePassword()
    {
        TenantSummary acme = createAcme();

        assertThat(acme).extracting(TenantSummary::code, TenantSummary::name, TenantSummary::status,
                TenantSummary::platform, TenantSummary::accounts)
                .containsExactly("acme", "Acme Corp", TenantStatus.ACTIVE, false, 1L);
        UserAccount boss = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("boss")).orElseThrow();
        assertThat(boss.getTenantId()).isEqualTo(acme.id());
        assertThat(boss.isSystemAccount()).isTrue();
        assertThat(boss.isMustChangePassword()).isTrue();
        assertThat(boss.getDisplayName()).isEqualTo("The Boss");
        assertThat(encoder.matches(PASSWORD, boss.getPasswordHash())).isTrue();
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getActorId, AuditEvent::getTargetId)
                .containsExactly(tuple(AuditAction.TENANT_CREATED, root,
                        Long.toString(acme.id())));
    }

    @Test
    void rejectsTakenCodesAndNamesAndInvalidInput()
    {
        createAcme();

        assertThatThrownBy(() -> asPlatform(() -> service.create(root, new TenantCommand("ACME", "Other", "other", null,
                PASSWORD)))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.TENANT_CODE_TAKEN));
        assertThatThrownBy(() -> asPlatform(() -> service.create(root, new TenantCommand("globex", "Globex", "BOSS", null,
                PASSWORD)))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.USERNAME_TAKEN));
        assertThatThrownBy(() -> asPlatform(() -> service.create(root, new TenantCommand("1x", "Globex", "g", null,
                PASSWORD)))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> asPlatform(() -> service.create(root, new TenantCommand("globex", "Globex", "gadmin",
                null, "short")))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT));
        assertThat(tenants.count()).isEqualTo(2);
    }

    @Test
    void listsAndSearchesTenantsWithTheirAccountCounts()
    {
        createAcme();
        asPlatform(() -> service.create(root, new TenantCommand("globex", "Globex 集团", "gadmin", null, PASSWORD)));

        PageResult<TenantSummary> all = asPlatform(() -> service.list(root, " ", new PageQuery(1, 10)));
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.items().get(0)).extracting(TenantSummary::code, TenantSummary::platform, TenantSummary::accounts)
                .containsExactly("default", true, 2L);
        assertThat(asPlatform(() -> service.list(root, "集团", new PageQuery(1, 10))).items())
                .extracting(TenantSummary::code).containsExactly("globex");
        // Wildcards in the search text are taken literally (dropped), not as "match everything".
        assertThat(asPlatform(() -> service.list(root, "%", new PageQuery(1, 10))).total()).isEqualTo(3);
        assertThat(asPlatform(() -> service.list(root, "_q_", new PageQuery(1, 10))).total()).isZero();
    }

    @Test
    void renamesSuspendsAndReactivatesTenants()
    {
        TenantSummary acme = createAcme();
        long boss = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("boss")).orElseThrow().requireId();
        TenantContext.runInTenant(acme.id(), () -> consoleSessions.start("boss-session", boss, null, null));

        assertThat(asPlatform(() -> service.rename(root, acme.id(), "Acme Group")).name()).isEqualTo("Acme Group");
        assertThatThrownBy(() -> asPlatform(() -> service.rename(root, acme.id(), " ")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));

        assertThat(asPlatform(() -> service.suspend(root, acme.id())).status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(((RecordingSessionTerminator) terminator).accounts()).containsExactly(boss);
        assertThat(TenantContext.callInTenant(acme.id(), () -> sessions.findByAccountId(boss))).isEmpty();

        assertThat(asPlatform(() -> service.activate(root, acme.id())).status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(asPlatform(() -> service.find(root, acme.id())).name()).isEqualTo("Acme Group");
        assertThat(events.findAll()).extracting(AuditEvent::getAction).containsSubsequence(AuditAction.TENANT_CREATED,
                AuditAction.TENANT_UPDATED, AuditAction.TENANT_SUSPENDED, AuditAction.TENANT_ACTIVATED);
    }

    @Test
    void thePlatformTenantStaysActiveAndUnknownTenantsAreNotFound()
    {
        assertThatThrownBy(() -> asPlatform(() -> service.suspend(root, platform)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.PLATFORM_TENANT_PROTECTED));
        assertThatThrownBy(() -> asPlatform(() -> service.activate(root, -1)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(tenants.findById(platform)).get().extracting(Tenant::getStatus).isEqualTo(TenantStatus.ACTIVE);
    }
}
