// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.ExternalIdentity;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, SecretBox.class, IdentitySourceSettings.class, LdapDirectory.class, ExternalAccounts.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExternalAccountsTest
{
    private static TestDirectory directory;

    @Autowired
    private ExternalAccounts externals;

    @Autowired
    private IdentitySourceSettings settings;

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
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void start() throws Exception
    {
        directory = TestDirectory.start().user("carol", "Carol C", "carol-secret");
    }

    @AfterAll
    static void stop()
    {
        directory.close();
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

    private <T> T inTenant(long tenant, Supplier<T> work)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }

    private IdentitySource source(long tenant, String code, LdapSettings ldap, boolean provisioning)
    {
        return inTenant(tenant, () -> {
            IdentitySource source = IdentitySource.create(code, IdentitySourceType.LDAP);
            source.configure(code, true, provisioning, IdentitySourceSettings.write(ldap), null);
            source.storeSecret(settings.seal(TestDirectory.BIND_PASSWORD));
            return sources.save(source);
        });
    }

    @Test
    void signsUpWithTheFirstDirectoryThatAcceptsTheUser()
    {
        long down = tenants.save(Tenant.create("down", "Down")).requireId();
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        source(down, "down", LdapSettings.of("ldap://localhost:1", TestDirectory.BASE), true);
        source(acme, "manual", directory.settings(false), false);
        source(acme, "corp", directory.settings(false), true);

        assertThat(externals.signUp("carol", "")).isEmpty();
        assertThat(externals.signUp("carol", "wrong")).isEmpty();
        long carol = externals.signUp("carol", "carol-secret").orElseThrow();

        UserAccount account = TenantContext.callAsSystem(() -> accounts.findById(carol)).orElseThrow();
        assertThat(account.getTenantId()).isEqualTo(acme);
        assertThat(inTenant(acme, () -> externals.isExternal(carol))).isTrue();
        // Signing up again finds the same account.
        assertThat(externals.signUp("carol", "carol-secret")).contains(carol);
    }

    @Test
    void checksPasswordsOnlyForAccountsOfAnEnabledDirectory()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        IdentitySource corp = source(acme, "corp", directory.settings(false), true);
        UserAccount local = inTenant(acme, () -> accounts.save(UserAccount.create("local", "{noop}x", Instant.EPOCH)));
        assertThat(inTenant(acme, () -> externals.checkPassword(local, "x"))).isEmpty();

        UserAccount carol = inTenant(acme, () -> externals.provision(corp, directory.find("carol")));
        assertThat(inTenant(acme, () -> externals.checkPassword(carol, "carol-secret"))).contains(true);
        assertThat(inTenant(acme, () -> externals.checkPassword(carol, "wrong"))).contains(false);

        // Another user who took the name later is not the account's user.
        UserAccount impostor = inTenant(acme, () -> {
            UserAccount account = accounts.save(UserAccount.create("carol2", "{noop}x", Instant.EPOCH));
            links.save(ExternalIdentity.of(account.requireId(), corp.requireId(), "someone-else"));
            return account;
        });
        assertThat(inTenant(acme, () -> externals.checkPassword(impostor, "carol-secret"))).contains(false);

        IdentitySource oidc = inTenant(acme, () -> {
            IdentitySource source = IdentitySource.create("okta", IdentitySourceType.OIDC);
            source.configure("Okta", true, true, IdentitySourceSettings.write(new OidcSettings("https://login.example.com", "c", "", "", "", "")),
                    null);
            return sources.save(source);
        });
        UserAccount federated = inTenant(acme, () -> externals.provision(oidc, new DirectoryUser("sub-1", "fiona", null, null)));
        assertThat(inTenant(acme, () -> externals.checkPassword(federated, "anything"))).contains(false);
    }

    @Test
    void createsAccountsOnlyWhenTheNameCanBeOne()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        IdentitySource closed = source(acme, "closed", directory.settings(false), false);
        IdentitySource open = source(acme, "open", directory.settings(false), true);

        assertThatThrownBy(() -> inTenant(acme, () -> externals.provision(closed, new DirectoryUser("1", "dora", null, null))))
                .isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> inTenant(acme, () -> externals.provision(open, new DirectoryUser("2", "no spaces allowed", null, null))))
                .isInstanceOf(GrantForgeException.class);
        // Names the account cannot hold are cut or left out.
        String longName = "N".repeat(200);
        UserAccount dora = inTenant(acme, () -> externals.provision(open, new DirectoryUser("3", "dora", longName, "not an address")));
        assertThat(dora.getDisplayName()).hasSize(UserAccount.MAX_DISPLAY_NAME);
        assertThat(dora.getEmail()).isNull();
        assertThat(externals.refresh(dora, new DirectoryUser("3", "dora", longName, null))).isFalse();
        assertThat(externals.refresh(dora, new DirectoryUser("3", "dora", "Dora", "dora@example.com"))).isTrue();
    }
}
