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
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, ConsoleSessionService.class, SecretBox.class, IdentitySourceSettings.class,
        LdapDirectory.class, ExternalAccounts.class, IdentitySourceService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentitySourceServiceTest
{
    private static TestDirectory directory;

    @Autowired
    private IdentitySourceService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private IdentitySourceRepository sources;

    @Autowired
    private ExternalIdentityRepository links;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private SessionTerminator terminator;

    private long tenant;

    @BeforeAll
    static void start() throws Exception
    {
        directory = TestDirectory.start().user("alice", "Alice A", "a").user("bob", "Bob B", "b").user("x", "Too short a name", "c");
    }

    @AfterAll
    static void stop()
    {
        directory.close();
    }

    @BeforeEach
    void createTenant()
    {
        ((RecordingSessionTerminator) terminator).clear();
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            links.deleteAllInBatch();
            accounts.deleteAllInBatch();
            sources.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> work)
    {
        return TenantContext.callInTenant(tenant, work::get);
    }

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    private IdentitySourceCommand ldap(String code, boolean disableMissing, @Nullable Integer interval)
    {
        return new IdentitySourceCommand(code, "Corporate LDAP", IdentitySourceType.LDAP, true, true, directory.settings(disableMissing), null,
                TestDirectory.BIND_PASSWORD, interval);
    }

    @Test
    void keepsSourcesWithoutShowingTheirSecret()
    {
        IdentitySourceView created = inTenant(() -> service.create(1, ldap("corp", false, null)));

        assertThat(created.code()).isEqualTo("corp");
        assertThat(created.secretSet()).isTrue();
        assertThat(created.ldap()).isEqualTo(directory.settings(false));
        assertThat(created.oidc()).isNull();
        assertThat(created.accounts()).isZero();
        // The secret is stored sealed.
        assertThat(inTenant(() -> sources.findById(created.id())).orElseThrow().getSecret()).doesNotContain(TestDirectory.BIND_PASSWORD);
        assertThat(inTenant(() -> service.list())).extracting(IdentitySourceView::code).containsExactly("corp");

        // Without a secret the stored one stays; a blank one removes it.
        IdentitySourceCommand renamed = new IdentitySourceCommand("ignored", "Head office", IdentitySourceType.LDAP, false, false,
                directory.settings(true), null, null, 60);
        IdentitySourceView updated = inTenant(() -> service.update(1, created.id(), renamed));
        assertThat(updated).extracting(IdentitySourceView::code, IdentitySourceView::name, IdentitySourceView::enabled,
                IdentitySourceView::provisioning, IdentitySourceView::secretSet, IdentitySourceView::syncIntervalMinutes)
                .containsExactly("corp", "Head office", false, false, true, 60);
        IdentitySourceCommand cleared = new IdentitySourceCommand("corp", "Head office", IdentitySourceType.LDAP, false, false,
                directory.settings(true), null, " ", null);
        assertThat(inTenant(() -> service.update(1, created.id(), cleared)).secretSet()).isFalse();

        OidcSettings provider = new OidcSettings("https://login.example.com/", "grantforge", "", "", "", "");
        IdentitySourceView oidc = inTenant(() -> service.create(1, new IdentitySourceCommand("okta", "Okta", IdentitySourceType.OIDC, true, true,
                null, provider, "client-secret", null)));
        assertThat(oidc.oidc()).isNotNull().extracting(OidcSettings::issuer).isEqualTo("https://login.example.com");
        assertThat(inTenant(() -> service.get(oidc.id())).ldap()).isNull();

        inTenant(() -> {
            service.delete(1, oidc.id());
            return null;
        });
        assertThatThrownBy(() -> inTenant(() -> service.get(oidc.id())))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(events.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.IDENTITY_SOURCE_CREATED,
                AuditAction.IDENTITY_SOURCE_UPDATED, AuditAction.IDENTITY_SOURCE_DELETED);
    }

    @Test
    void refusesInvalidSources()
    {
        inTenant(() -> service.create(1, ldap("corp", false, null)));
        long other = tenants.save(Tenant.create("globex", "Globex")).requireId();

        // Codes are unique on the platform, as sign-in links name them.
        assertThatThrownBy(() -> TenantContext.callInTenant(other, () -> service.create(1, ldap("corp", false, null))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_CODE_TAKEN));
        for (IdentitySourceCommand invalid : new IdentitySourceCommand[] {
            ldap("Bad Code", false, null),
            ldap("corp2", false, 5),
            new IdentitySourceCommand("corp3", " ", IdentitySourceType.LDAP, true, true, directory.settings(false), null, null, null),
            new IdentitySourceCommand("corp4", "No settings", IdentitySourceType.LDAP, true, true, null, null, null, null),
            new IdentitySourceCommand("corp5", "Interval", IdentitySourceType.OIDC, true, true, null,
                    new OidcSettings("https://login.example.com", "c", "", "", "", ""), null, 60),
        }) {
            assertThatThrownBy(() -> inTenant(() -> service.create(1, invalid)))
                    .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_INVALID));
        }
        long id = inTenant(() -> service.list()).get(0).id();
        IdentitySourceCommand retyped = new IdentitySourceCommand("corp", "x", IdentitySourceType.OIDC, true, true, null,
                new OidcSettings("https://login.example.com", "c", "", "", "", ""), null, null);
        assertThatThrownBy(() -> inTenant(() -> service.update(1, id, retyped)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_INVALID));
    }

    @Test
    void testsDirectories()
    {
        long id = inTenant(() -> service.create(1, ldap("corp", false, null))).id();
        inTenant(() -> {
            service.test(id);
            return null;
        });
        IdentitySourceCommand wrong = new IdentitySourceCommand("corp", "Corporate LDAP", IdentitySourceType.LDAP, true, true,
                directory.settings(false), null, "wrong", null);
        inTenant(() -> service.update(1, id, wrong));
        assertThatThrownBy(() -> inTenant(() -> {
            service.test(id);
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_UNAVAILABLE));
        // Nor can a failing directory be synced; the failure is noted on the source.
        assertThatThrownBy(() -> inTenant(() -> service.sync(1L, id))).isInstanceOf(GrantForgeException.class);
        assertThat(inTenant(() -> service.get(id)).lastSyncSummary()).startsWith("failed: ");

        long oidc = inTenant(() -> service.create(1, new IdentitySourceCommand("okta", "Okta", IdentitySourceType.OIDC, true, true, null,
                new OidcSettings("https://login.example.com", "c", "", "", "", ""), null, null))).id();
        assertThatThrownBy(() -> inTenant(() -> {
            service.test(oidc);
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_NOT_SYNCABLE));
        assertThatThrownBy(() -> inTenant(() -> service.sync(1L, oidc)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_NOT_SYNCABLE));
    }

    @Test
    void syncsUsersIntoAccounts() throws Exception
    {
        long id = inTenant(() -> service.create(1, ldap("corp", true, 15))).id();
        // A local account with a directory user's name is left alone.
        inTenant(() -> accounts.save(UserAccount.create("bob", "{noop}x", Instant.EPOCH)));

        SyncReport first = inTenant(() -> service.sync(1L, id));

        assertThat(first).isEqualTo(new SyncReport(3, 1, 0, 0, List.of("bob", "x")));
        UserAccount alice = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("alice")).orElseThrow();
        assertThat(alice.getTenantId()).isEqualTo(tenant);
        assertThat(alice.getDisplayName()).isEqualTo("Alice A");
        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(inTenant(() -> service.get(id))).extracting(IdentitySourceView::accounts, IdentitySourceView::lastSyncSummary)
                .containsExactly(1L, "found 3, created 1, updated 0, disabled 0; 2 skipped: bob, x");

        directory.rename("alice", "Alice Anders");
        assertThat(inTenant(() -> service.sync(null, id))).extracting(SyncReport::created, SyncReport::updated).containsExactly(0, 1);
        assertThat(TenantContext.callAsSystem(() -> accounts.findById(alice.requireId())).orElseThrow().getDisplayName())
                .isEqualTo("Alice Anders");

        // Users who left the directory lose their account, and their sessions end.
        directory.remove("alice");
        try {
            assertThat(inTenant(() -> service.sync(1L, id)).disabled()).isEqualTo(1);
            assertThat(TenantContext.callAsSystem(() -> accounts.findById(alice.requireId())).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.DISABLED);
            assertThat(((RecordingSessionTerminator) terminator).accounts()).containsExactly(alice.requireId());
        }
        finally {
            directory.user("alice", "Alice A", "a");
        }
        assertThat(events.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.ACCOUNT_PROVISIONED, AuditAction.IDENTITY_SOURCE_SYNCED);
        // A source accounts sign in with cannot be deleted.
        assertThatThrownBy(() -> inTenant(() -> {
            service.delete(1, id);
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_IN_USE));
    }

    @Test
    void offersTheEnabledProvidersOfEveryTenantOnTheSignInPage()
    {
        OidcSettings provider = new OidcSettings("https://login.example.com", "c", "", "", "", "");
        inTenant(() -> service.create(1, new IdentitySourceCommand("okta", "Okta", IdentitySourceType.OIDC, true, true, null, provider, null, null)));
        inTenant(() -> service.create(1, new IdentitySourceCommand("old", "Old", IdentitySourceType.OIDC, false, true, null, provider, null, null)));
        inTenant(() -> service.create(1, ldap("corp", false, null)));

        assertThat(service.signInOptions()).containsExactly(new SignInOption("okta", "Okta"));
    }

    @Test
    void findsTheDirectoriesDueForSync()
    {
        long due = inTenant(() -> service.create(1, ldap("corp", false, 15))).id();
        inTenant(() -> service.create(1, ldap("manual", false, null)));
        Instant now = Instant.now();

        assertThat(service.dueForSync(now)).extracting(source -> source.requireId()).containsExactly(due);
        inTenant(() -> service.sync(1L, due));
        assertThat(service.dueForSync(now.plusSeconds(60))).isEmpty();
        assertThat(service.dueForSync(now.plusSeconds(16 * 60))).hasSize(1);
    }
}
