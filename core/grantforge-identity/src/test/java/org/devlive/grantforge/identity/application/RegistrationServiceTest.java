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

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class, RegistrationService.class})
@TestPropertySource(properties = "grantforge.security.registration-enabled=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegistrationServiceTest
{
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private RegistrationService registration;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private AuditLog audit;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("default", "Default").markPlatform()).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private static ErrorCode codeOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    private RegistrationService registration(boolean enabled, String tenantCode)
    {
        return new RegistrationService(new SecurityProperties(enabled, SecurityProperties.Password.defaults(),
                SecurityProperties.Lockout.defaults()), tenantCode, tenants, accounts, passwords, audit, transactionManager,
                Clock.systemUTC());
    }

    @Test
    void visitorsGetAnOrdinaryAccountInTheRegistrationTenant()
    {
        assertThat(registration.register("Visitor", PASSWORD, "Vi Sitor")).isEqualTo("Visitor");

        UserAccount stored = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("visitor")).orElseThrow();
        assertThat(stored.getTenantId()).isEqualTo(tenant);
        assertThat(stored.isSystemAccount()).isFalse();
        assertThat(stored.isMustChangePassword()).isFalse();
        assertThat(stored.getDisplayName()).isEqualTo("Vi Sitor");
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getActorName)
                .containsExactly(tuple(AuditAction.USER_REGISTERED, "Visitor"));
    }

    @Test
    void refusesTakenNamesWeakPasswordsAndBadNames()
    {
        TenantContext.runInTenant(tenant, () -> accounts.save(UserAccount.create("taken", "h", Instant.EPOCH)));

        assertThatThrownBy(() -> registration.register("TAKEN", PASSWORD, null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.USERNAME_TAKEN));
        assertThatThrownBy(() -> registration.register("short", "short", null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT));
        assertThatThrownBy(() -> registration.register("a b", PASSWORD, null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void staysClosedWhenDisabledOrTheTenantIsUnavailable()
    {
        assertThatThrownBy(() -> registration(false, "default").register("visitor", PASSWORD, null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.REGISTRATION_CLOSED));
        assertThatThrownBy(() -> registration(true, "nowhere").register("visitor", PASSWORD, null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.REGISTRATION_CLOSED));
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                tenants.findById(acme).orElseThrow().suspend());
        assertThatThrownBy(() -> registration(true, " ACME ").register("visitor", PASSWORD, null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.REGISTRATION_CLOSED));
        assertThat(TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("visitor"))).isEmpty();
    }
}
