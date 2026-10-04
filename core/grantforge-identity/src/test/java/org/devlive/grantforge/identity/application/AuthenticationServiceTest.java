// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Digests;
import org.devlive.grantforge.identity.domain.MfaFactorRepository;
import org.devlive.grantforge.identity.domain.MfaRecoveryCodeRepository;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class, AuthenticationService.class,
        MfaService.class, SecretBox.class, AuthenticationServiceTest.TestClock.class})
@TestPropertySource(properties = {"grantforge.security.lockout.max-attempts=3", "grantforge.security.lockout.duration=10m",
        "grantforge.security.password.max-age=30d"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthenticationServiceTest
{
    private static final Instant START = Instant.parse("2026-06-01T09:00:00Z");
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestClock clock;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private MfaService mfa;

    @Autowired
    private MfaFactorRepository factors;

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodes;

    private long tenant;
    private long account;

    /** A clock the tests move forward. */
    static class TestClock
    {
        private volatile Instant now = START;

        @Bean
        @Primary
        Clock testClock()
        {
            return new Clock()
            {
                @Override
                public ZoneOffset getZone()
                {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone)
                {
                    return this;
                }

                @Override
                public Instant instant()
                {
                    return now;
                }
            };
        }

        void set(Instant instant)
        {
            now = instant;
        }
    }

    @BeforeEach
    void createAccount()
    {
        clock.set(START);
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        account = TenantContext.callInTenant(tenant, () -> accounts.save(
                UserAccount.create("Alice", encoder.encode(PASSWORD), START).withDisplayName("Alice A")).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            recoveryCodes.deleteAllInBatch();
            factors.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private List<String> trail()
    {
        return events.findAll(Sort.by("occurredAt", "id")).stream()
                .map(event -> event.getAction() + ":" + event.getActorId() + ":" + event.getActorName() + ":" + event.getReason())
                .toList();
    }

    private void change(Consumer<UserAccount> change)
    {
        TenantContext.runInTenant(tenant, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> change.accept(accounts.findById(account).orElseThrow())));
    }

    private UserAccount load()
    {
        return TenantContext.callInTenant(tenant, () -> accounts.findById(account)).orElseThrow();
    }

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void correctCredentialsSignInCaseInsensitively()
    {
        SignedInAccount signedIn = authentication.authenticate(" ALICE ", PASSWORD);

        assertThat(signedIn).isEqualTo(new SignedInAccount(account, tenant, "Alice", "Alice A", false, false));
        assertThat(load().getLastLoginAt()).isEqualTo(START);
    }

    @Test
    void unknownNamesAndWrongPasswordsLookTheSame()
    {
        assertThatThrownBy(() -> authentication.authenticate("nobody", PASSWORD))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
        assertThatThrownBy(() -> authentication.authenticate(null, null))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
        assertThatThrownBy(() -> authentication.authenticate("alice", "wrong"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
        assertThat(load().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void repeatedFailuresLockTheAccountForAWhile()
    {
        authentication.authenticate("alice", PASSWORD);
        assertThatThrownBy(() -> authentication.authenticate("alice", "wrong-1")).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> authentication.authenticate("alice", "wrong-2")).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> authentication.authenticate("alice", "wrong-3"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_LOCKED));

        // Even the right password is refused while locked.
        assertThatThrownBy(() -> authentication.authenticate("alice", PASSWORD))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_LOCKED));

        clock.set(START.plusSeconds(600));
        assertThat(authentication.authenticate("alice", PASSWORD).accountId()).isEqualTo(account);
        assertThat(load().getFailedAttempts()).isZero();
    }

    @Test
    void anAdministratorsLockAsksToContactTheAdministrator()
    {
        change(UserAccount::lockIndefinitely);

        assertThatThrownBy(() -> authentication.authenticate("alice", PASSWORD))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_LOCKED_BY_ADMINISTRATOR));
        change(UserAccount::unlock);
        assertThat(authentication.authenticate("alice", PASSWORD).accountId()).isEqualTo(account);
    }

    @Test
    void everyAttemptIsAuditedWithItsReason()
    {
        authentication.authenticate("ALICE", PASSWORD);
        assertThatThrownBy(() -> authentication.authenticate("nobody", PASSWORD)).isInstanceOf(GrantForgeException.class);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> authentication.authenticate("alice", "wrong")).isInstanceOf(GrantForgeException.class);
        }
        clock.set(START.plusSeconds(1));
        assertThatThrownBy(() -> authentication.authenticate("alice", PASSWORD)).isInstanceOf(GrantForgeException.class);

        assertThat(trail()).containsExactly(
                "LOGIN_SUCCEEDED:" + account + ":alice:null",
                "LOGIN_FAILED:null:nobody:GF-IDENTITY-020",
                "LOGIN_FAILED:" + account + ":alice:GF-IDENTITY-020",
                "LOGIN_FAILED:" + account + ":alice:GF-IDENTITY-020",
                "ACCOUNT_LOCKED:" + account + ":alice:null",
                "LOGIN_FAILED:" + account + ":alice:GF-IDENTITY-021",
                "LOGIN_FAILED:" + account + ":alice:GF-IDENTITY-021");
        assertThat(events.findAll()).filteredOn(event -> event.getAction() == AuditAction.LOGIN_SUCCEEDED)
                .extracting(AuditEvent::getTenantId).containsExactly(tenant);
    }

    @Test
    void disabledAccountsAndSuspendedTenantsAreReportedOnlyWithTheRightPassword()
    {
        change(UserAccount::disable);
        assertThatThrownBy(() -> authentication.authenticate("alice", "wrong"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
        assertThatThrownBy(() -> authentication.authenticate("alice", PASSWORD))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED));

        change(UserAccount::enable);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                tenants.findById(tenant).orElseThrow().suspend());
        assertThatThrownBy(() -> authentication.authenticate("alice", PASSWORD))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.TENANT_SUSPENDED));
    }

    @Test
    void legacyHashesAreUpgradedAtSignIn()
    {
        change(user -> user.rehashPassword("{sha256-legacy}" + Digests.sha256Hex("old-system-password")));

        authentication.authenticate("alice", "old-system-password");

        assertThat(load().getPasswordHash()).startsWith("{argon2}");
    }

    @Test
    void expiredOrResetPasswordsMustBeChanged()
    {
        clock.set(START.plus(Duration.ofDays(30)));
        assertThat(authentication.authenticate("alice", PASSWORD).passwordChangeRequired()).isTrue();

        clock.set(START);
        change(UserAccount::requirePasswordChange);
        assertThat(authentication.authenticate("alice", PASSWORD).passwordChangeRequired()).isTrue();
    }

    /** Turns two-step sign-in on and returns the authenticator's key and the recovery codes. */
    private byte[] enableMfa(List<String> recovery)
    {
        byte[] key = TenantContext.callInTenant(tenant, () -> Totp.fromBase32(mfa.enroll(account).secret()));
        recovery.addAll(TenantContext.callInTenant(tenant, () -> mfa.confirm(account, Totp.code(key, Totp.step(START)))));
        return key;
    }

    @Test
    void anAccountWithTwoStepSignInNeedsItsSecondFactor()
    {
        List<String> recovery = new ArrayList<>();
        byte[] key = enableMfa(recovery);
        events.deleteAllInBatch();

        SignedInAccount half = authentication.authenticate("alice", PASSWORD);
        assertThat(half.secondFactorRequired()).isTrue();
        // The password alone is not a sign-in.
        assertThat(load().getLastLoginAt()).isNull();
        assertThat(trail()).isEmpty();

        // The code that confirmed the authenticator does not work twice.
        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, Totp.code(key, Totp.step(START))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_CODE_INVALID));
        clock.set(START.plusSeconds(30));
        SignedInAccount full = authentication.completeSecondFactor(account, tenant, Totp.code(key, Totp.step(START.plusSeconds(30))));
        assertThat(full).isEqualTo(new SignedInAccount(account, tenant, "Alice", "Alice A", false, false));
        assertThat(load().getLastLoginAt()).isEqualTo(START.plusSeconds(30));
        assertThat(load().getFailedAttempts()).isZero();

        // A recovery code also works, once.
        assertThat(authentication.completeSecondFactor(account, tenant, recovery.get(0).toUpperCase(Locale.ROOT)).accountId())
                .isEqualTo(account);
        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, recovery.get(0)))
                .isInstanceOf(GrantForgeException.class);
        assertThat(trail()).containsExactly(
                "LOGIN_FAILED:" + account + ":alice:GF-IDENTITY-100",
                "LOGIN_SUCCEEDED:" + account + ":alice:null",
                "MFA_RECOVERY_CODE_USED:" + account + ":null:9 left",
                "LOGIN_SUCCEEDED:" + account + ":alice:null",
                "LOGIN_FAILED:" + account + ":alice:GF-IDENTITY-100");
    }

    @Test
    void wrongSecondFactorsCountTowardsTheLockout()
    {
        enableMfa(new ArrayList<>());

        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, "000000")).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, null)).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, "not a code"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_LOCKED));
        assertThatThrownBy(() -> authentication.authenticate("alice", PASSWORD))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_LOCKED));

        // An account disabled or removed between the two steps is refused.
        clock.set(START.plusSeconds(600));
        change(UserAccount::disable);
        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, "000000"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED));
        assertThatThrownBy(() -> authentication.completeSecondFactor(account + 1000, tenant, "000000"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
        change(UserAccount::lockIndefinitely);
        assertThatThrownBy(() -> authentication.completeSecondFactor(account, tenant, "000000"))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_LOCKED_BY_ADMINISTRATOR));
    }

    @Test
    void aStepUpChecksTheSecondFactorWithoutSigningIn()
    {
        byte[] key = enableMfa(new ArrayList<>());
        events.deleteAllInBatch();

        assertThatThrownBy(() -> authentication.stepUp(account, tenant, Totp.code(key, Totp.step(START))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_CODE_INVALID));
        authentication.stepUp(account, tenant, Totp.code(key, Totp.step(START) + 1));

        assertThat(load().getLastLoginAt()).isNull();
        assertThat(load().getFailedAttempts()).isEqualTo(1);
        assertThat(trail()).containsExactly("MFA_STEP_UP:" + account + ":alice:GF-IDENTITY-100", "MFA_STEP_UP:" + account + ":alice:null");
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> authentication.stepUp(account, tenant, null)).isInstanceOf(GrantForgeException.class);
        }
        assertThat(trail()).contains("ACCOUNT_LOCKED:" + account + ":alice:null");
    }
}
