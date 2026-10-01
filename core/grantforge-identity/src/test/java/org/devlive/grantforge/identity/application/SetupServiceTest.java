// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.PlatformSetting;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class, SetupService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SetupServiceTest
{
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private SetupService setup;

    @Autowired
    private PlatformSettingRepository settings;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PasswordService passwords;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        settings.deleteAllInBatch();
    }

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void tokensAreRandomAndOnlyTheirHashIsStored()
    {
        String first = setup.issueToken().orElseThrow();
        String second = setup.issueToken().orElseThrow();

        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(settings.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.getSettingKey()).isEqualTo(SetupService.TOKEN_HASH);
            assertThat(stored.getSettingValue()).isEqualTo(SetupService.sha256(second));
        });
    }

    @Test
    void setupCreatesTheTenantAndAdministratorOnce()
    {
        String token = setup.issueToken().orElseThrow();

        SetupResult result = setup.complete(new SetupCommand(token, " ", "Admin", PASSWORD, "Administrator"));

        assertThat(result).isEqualTo(new SetupResult("default", "Admin"));
        assertThat(setup.isRequired()).isFalse();
        assertThat(tenants.findAll()).singleElement().satisfies(tenant -> assertThat(tenant.getName()).isEqualTo("Default"));
        UserAccount admin = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("admin")).orElseThrow();
        assertThat(admin.isSystemAccount()).isTrue();
        assertThat(admin.getDisplayName()).isEqualTo("Administrator");
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(settings.findBySettingKey(SetupService.TOKEN_HASH)).isEmpty();

        assertThatThrownBy(() -> setup.complete(new SetupCommand(token, null, "other", PASSWORD, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.SETUP_COMPLETED));
        assertThat(setup.issueToken()).isEmpty();
    }

    @Test
    void wrongOrMissingTokensAreRejected()
    {
        assertThatThrownBy(() -> setup.complete(new SetupCommand("anything", null, "admin", PASSWORD, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.SETUP_TOKEN_INVALID));

        setup.issueToken();
        assertThatThrownBy(() -> setup.complete(new SetupCommand("wrong", null, "admin", PASSWORD, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.SETUP_TOKEN_INVALID));
        assertThatThrownBy(() -> setup.complete(new SetupCommand(null, null, "admin", PASSWORD, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.SETUP_TOKEN_INVALID));
        assertThat(setup.isRequired()).isTrue();
    }

    @Test
    void invalidAdministratorsAreRejectedWithoutSideEffects()
    {
        String token = setup.issueToken().orElseThrow();

        assertThatThrownBy(() -> setup.complete(new SetupCommand(token, null, "admin", "short", null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT));
        assertThatThrownBy(() -> setup.complete(new SetupCommand(token, null, "no spaces", PASSWORD, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThat(setup.isRequired()).isTrue();
        assertThat(tenants.count()).isZero();
    }

    @Test
    void configuredTokensAreAcceptedButNeverReturned()
    {
        String token = "configured-token-0123456789";
        SetupService configured = new SetupService(settings, tenants, accounts, passwords, new SetupProperties(token), transactionManager, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThat(configured.issueToken()).isEmpty();
        assertThat(settings.findBySettingKey(SetupService.TOKEN_HASH)).get()
                .extracting(PlatformSetting::getSettingValue).isEqualTo(SetupService.sha256(token));
    }

    @Test
    @SuppressWarnings("unchecked")
    void concurrentCompletionLosesWithSetupCompleted()
    {
        PlatformSettingRepository racing = mock(PlatformSettingRepository.class);
        String token = "racing-token-0123456789";
        when(racing.findBySettingKey(SetupService.COMPLETED_AT)).thenReturn(Optional.empty());
        when(racing.findBySettingKey(SetupService.TOKEN_HASH))
                .thenReturn(Optional.of(PlatformSetting.of(SetupService.TOKEN_HASH, SetupService.sha256(token))));
        when(racing.saveAndFlush(any(PlatformSetting.class))).thenThrow(DataIntegrityViolationException.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        SetupService service = new SetupService(racing, tenants, accounts, passwords, new SetupProperties(null), transactions, Clock.systemUTC());

        assertThatThrownBy(() -> service.complete(new SetupCommand(token, null, "admin", PASSWORD, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(IdentityErrorCode.SETUP_COMPLETED));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void nullCommandsAreRejected()
    {
        assertThatThrownBy(() -> setup.complete(null)).isInstanceOf(NullPointerException.class);
    }
}
