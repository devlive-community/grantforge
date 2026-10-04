// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, MfaService.class, SecretBox.class, MfaServiceTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MfaServiceTest
{
    private static final Instant NOW = Instant.parse("2026-06-01T09:00:00Z");

    @Autowired
    private MfaService mfa;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private MfaFactorRepository factors;

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodes;

    @Autowired
    private AuditEventRepository events;

    private long tenant;
    private long account;

    /** The clock stands still, so the codes the tests compute are current. */
    static class FixedClock
    {
        @Bean
        @Primary
        Clock fixedClock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void createAccount()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        account = inTenant(() -> accounts.save(UserAccount.create("Alice", "{noop}x", NOW)).requireId());
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

    private <T> T inTenant(Supplier<T> work)
    {
        return TenantContext.callInTenant(tenant, work::get);
    }

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    private static String code(byte[] key, long offset)
    {
        return Totp.code(key, Totp.step(NOW) + offset);
    }

    private List<String> trail()
    {
        return events.findAll(Sort.by("occurredAt", "id")).stream()
                .map(event -> event.getAction() + ":" + event.getActorId() + ":" + event.getTargetId() + ":" + event.getReason()).toList();
    }

    @Test
    void enrollingGivesALinkForAuthenticatorApps()
    {
        MfaEnrollment enrollment = inTenant(() -> mfa.enroll(account));

        assertThat(enrollment.secret()).matches("[A-Z2-7]{32}");
        assertThat(enrollment.uri()).isEqualTo("otpauth://totp/GrantForge%3AAlice?secret=" + enrollment.secret()
                + "&issuer=GrantForge&algorithm=SHA1&digits=6&period=30");
        // The secret is stored sealed.
        assertThat(inTenant(() -> factors.findByAccountId(account)).orElseThrow().getSecret()).doesNotContain(enrollment.secret());
        assertThat(inTenant(() -> mfa.status(account))).isEqualTo(new MfaStatus(false, 0));

        // Enrolling again before confirming replaces the secret.
        MfaEnrollment again = inTenant(() -> mfa.enroll(account));
        assertThat(again.secret()).isNotEqualTo(enrollment.secret());
        byte[] stale = Totp.fromBase32(enrollment.secret());
        assertThatThrownBy(() -> inTenant(() -> mfa.confirm(account, code(stale, 0))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_CODE_INVALID));
    }

    @Test
    void confirmingTurnsItOnWithTenRecoveryCodes()
    {
        assertThatThrownBy(() -> inTenant(() -> mfa.confirm(account, "123456")))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_NOT_ENROLLING));
        byte[] key = Totp.fromBase32(inTenant(() -> mfa.enroll(account)).secret());

        List<String> codes = inTenant(() -> mfa.confirm(account, code(key, 0)));

        assertThat(codes).hasSize(MfaService.RECOVERY_CODES).doesNotHaveDuplicates().allMatch(code -> code.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        assertThat(inTenant(() -> mfa.status(account))).isEqualTo(new MfaStatus(true, 10));
        assertThat(inTenant(() -> mfa.enabled(account))).isTrue();
        assertThatThrownBy(() -> inTenant(() -> mfa.enroll(account)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_ALREADY_ENABLED));
        assertThatThrownBy(() -> inTenant(() -> mfa.confirm(account, code(key, 1))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_NOT_ENROLLING));
        assertThat(trail()).containsExactly("MFA_ENABLED:" + account + ":null:null");
    }

    @Test
    void eachCodeWorksOnce()
    {
        byte[] key = Totp.fromBase32(inTenant(() -> mfa.enroll(account)).secret());
        List<String> codes = inTenant(() -> mfa.confirm(account, code(key, -1)));

        assertThat(inTenant(() -> mfa.verify(account, code(key, -1)))).isFalse();
        assertThat(inTenant(() -> mfa.verify(account, code(key, 0)))).isTrue();
        assertThat(inTenant(() -> mfa.verify(account, code(key, 0)))).isFalse();
        // An earlier code than the last one used does not work either.
        assertThat(inTenant(() -> mfa.verify(account, code(key, 1)))).isTrue();
        assertThat(inTenant(() -> mfa.verify(account, code(key, 0)))).isFalse();

        assertThat(inTenant(() -> mfa.verify(account, " " + codes.get(3).replace("-", "").toUpperCase(Locale.ROOT)))).isTrue();
        assertThat(inTenant(() -> mfa.verify(account, codes.get(3)))).isFalse();
        assertThat(inTenant(() -> mfa.verify(account, "wrong-codes"))).isFalse();
        assertThat(inTenant(() -> mfa.status(account)).recoveryCodesLeft()).isEqualTo(9);
    }

    @Test
    void verifyingAnAccountWithoutItFails()
    {
        assertThat(inTenant(() -> mfa.verify(account, "123456"))).isFalse();
        inTenant(() -> mfa.enroll(account));
        assertThat(inTenant(() -> mfa.verify(account, "123456"))).isFalse();
        assertThatThrownBy(() -> inTenant(() -> {
            mfa.disable(account, "123456");
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_NOT_ENABLED));
        assertThatThrownBy(() -> inTenant(() -> mfa.renewRecoveryCodes(account, "123456")))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_NOT_ENABLED));
    }

    @Test
    void renewingAndTurningOffNeedASecondFactor()
    {
        byte[] key = Totp.fromBase32(inTenant(() -> mfa.enroll(account)).secret());
        List<String> first = inTenant(() -> mfa.confirm(account, code(key, -1)));

        assertThatThrownBy(() -> inTenant(() -> mfa.renewRecoveryCodes(account, "000000-bad")))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_CODE_INVALID));
        List<String> renewed = inTenant(() -> mfa.renewRecoveryCodes(account, code(key, 0)));
        assertThat(renewed).hasSize(10).doesNotContainAnyElementsOf(first);
        assertThat(inTenant(() -> mfa.verify(account, first.get(0)))).isFalse();

        assertThatThrownBy(() -> inTenant(() -> {
            mfa.disable(account, first.get(1));
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_CODE_INVALID));
        inTenant(() -> {
            mfa.disable(account, renewed.get(0));
            return null;
        });

        assertThat(inTenant(() -> mfa.status(account))).isEqualTo(new MfaStatus(false, 0));
        assertThat(trail()).containsExactly("MFA_ENABLED:" + account + ":null:null",
                "MFA_RECOVERY_CODES_RENEWED:" + account + ":null:null",
                "MFA_RECOVERY_CODE_USED:" + account + ":null:9 left",
                "MFA_DISABLED:" + account + ":null:null");
    }

    @Test
    void anAdministratorCanResetIt()
    {
        assertThatThrownBy(() -> inTenant(() -> {
            mfa.reset(1, account + 1000);
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inTenant(() -> {
            mfa.reset(1, account);
            return null;
        })).satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.MFA_NOT_ENABLED));
        byte[] key = Totp.fromBase32(inTenant(() -> mfa.enroll(account)).secret());
        inTenant(() -> mfa.confirm(account, code(key, 0)));

        inTenant(() -> {
            mfa.reset(1, account);
            return null;
        });

        assertThat(inTenant(() -> mfa.status(account))).isEqualTo(new MfaStatus(false, 0));
        assertThat(inTenant(() -> factors.findByAccountId(account))).isEmpty();
        assertThat(trail()).endsWith("MFA_RESET:1:" + account + ":null");
        // The user can set an authenticator up again.
        assertThat(inTenant(() -> mfa.enroll(account)).secret()).isNotBlank();
    }
}
