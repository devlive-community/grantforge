// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Digests;
import org.devlive.grantforge.identity.domain.PasswordHistory;
import org.devlive.grantforge.identity.domain.PasswordHistoryRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs every change inside a tenant-bound transaction, as the application does. */
@DataJpaTest
@Import({IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class})
@TestPropertySource(properties = "grantforge.security.password.history-size=3")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordServiceTest
{
    private static final Instant NOW = Instant.parse("2026-05-01T08:00:00Z");

    @Autowired
    private PasswordService passwords;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordHistoryRepository history;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            history.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private long account(String passwordHash)
    {
        return TenantContext.callInTenant(tenant,
                () -> accounts.save(UserAccount.create("alice", passwordHash, NOW)).requireId());
    }

    /** Loads the account in a tenant-bound transaction, applies the change and commits it. */
    private void inTransaction(long id, Consumer<UserAccount> change)
    {
        TenantContext.runInTenant(tenant, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> change.accept(accounts.findById(id).orElseThrow())));
    }

    private UserAccount load(long id)
    {
        return TenantContext.callInTenant(tenant, () -> accounts.findById(id)).orElseThrow();
    }

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void hashNewAppliesThePolicy()
    {
        assertThat(passwords.hashNew("a long enough password", "alice")).startsWith("{argon2}");
        assertThatThrownBy(() -> passwords.hashNew("short", "alice"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT));
    }

    @Test
    void verifyingAnImportedLegacyHashUpgradesIt()
    {
        long id = account("{sha256-legacy}" + Digests.sha256Hex("legacy-password"));

        inTransaction(id, account -> assertThat(passwords.verify(account, "wrong")).isFalse());
        assertThat(load(id).getPasswordHash()).startsWith("{sha256-legacy}");

        inTransaction(id, account -> assertThat(passwords.verify(account, "legacy-password")).isTrue());
        UserAccount upgraded = load(id);
        assertThat(upgraded.getPasswordHash()).startsWith("{argon2}");
        assertThat(upgraded.getPasswordChangedAt()).isEqualTo(NOW);
        inTransaction(id, account -> {
            assertThat(passwords.verify(account, "legacy-password")).isTrue();
            assertThat(passwords.verify(account, null)).isFalse();
            assertThat(passwords.verify(account, "")).isFalse();
        });
    }

    @Test
    void changeRequiresTheCurrentPasswordAndRejectsRecentOnes()
    {
        long id = account(encoder.encode("password-one"));
        Instant later = NOW.plusSeconds(60);

        assertThatThrownBy(() -> inTransaction(id, account -> passwords.change(account, "wrong", "password-two", later)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_INCORRECT));
        assertThatThrownBy(() -> inTransaction(id, account -> passwords.change(account, "password-one", "password-one",
                later))).isInstanceOfSatisfying(GrantForgeException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_REUSED);
                    assertThat(error.getArguments()).containsExactly(3);
                });

        inTransaction(id, account -> passwords.change(account, "password-one", "password-two", later));
        inTransaction(id, account -> passwords.change(account, "password-two", "password-three", later));
        assertThat(load(id).getPasswordChangedAt()).isEqualTo(later);
        assertThatThrownBy(() -> inTransaction(id, account -> passwords.replace(account, "password-one", later)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_REUSED));

        // Only history-size - 1 former passwords are kept: the current one counts as the newest.
        inTransaction(id, account -> passwords.replace(account, "password-four", later));
        assertThat(TenantContext.callInTenant(tenant, () -> history.findByAccountIdOrderByCreatedAtDescIdDesc(id)))
                .extracting(PasswordHistory::getPasswordHash).hasSize(2)
                .allSatisfy(hash -> assertThat(encoder.matches("password-one", hash)).isFalse());
        inTransaction(id, account -> passwords.replace(account, "password-one", later));
    }

    @Test
    void historyCanBeDisabled()
    {
        SecurityProperties defaults = new SecurityProperties(false, SecurityProperties.Password.defaults(),
                SecurityProperties.Lockout.defaults());
        PasswordService forgetful = new PasswordService(encoder, new PasswordPolicy(defaults), history, defaults);
        long id = account(encoder.encode("password-one"));

        inTransaction(id, account -> forgetful.replace(account, "password-one", NOW));

        assertThat(TenantContext.callInTenant(tenant, () -> history.count())).isZero();
    }

    @Test
    void passwordsExpireOnlyWhenAMaximumAgeIsSet()
    {
        UserAccount account = UserAccount.create("bob", "h", NOW);
        SecurityProperties expiring = new SecurityProperties(false,
                new SecurityProperties.Password(12, 128, 1, 0, Duration.ofDays(90), StandardCharsets.UTF_8),
                SecurityProperties.Lockout.defaults());
        PasswordService strict = new PasswordService(encoder, new PasswordPolicy(expiring), history, expiring);

        assertThat(passwords.isExpired(account, NOW.plus(Duration.ofDays(3650)))).isFalse();
        assertThat(strict.isExpired(account, NOW.plus(Duration.ofDays(90)).minusSeconds(1))).isFalse();
        assertThat(strict.isExpired(account, NOW.plus(Duration.ofDays(90)))).isTrue();
    }
}
