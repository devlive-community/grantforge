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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class, ProfileService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfileServiceTest
{
    @Autowired
    private ProfileService profiles;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private IdentitySourceRepository sources;

    @Autowired
    private ExternalIdentityRepository links;

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

    @Test
    void describesTheAccountWithItsTenant()
    {
        long tenant = tenants.save(Tenant.create("acme", "Acme Corp")).requireId();
        UserAccount created = UserAccount.create("alice", "h", Instant.EPOCH).withDisplayName("Alice").markSystemAccount();
        created.requirePasswordChange();
        long account = TenantContext.callInTenant(tenant, () -> accounts.save(created).requireId());

        assertThat(TenantContext.callInTenant(tenant, () -> profiles.find(account))).get().isEqualTo(new AccountProfile(
                account, "alice", "Alice", null, "acme", "Acme Corp", true, true, null, null));
        // Another tenant cannot see the account.
        assertThat(TenantContext.callInTenant(tenant + 1, () -> profiles.find(account))).isEmpty();
    }

    @Test
    void accountsOfAnIdentitySourceKeepTheirPasswordThere()
    {
        long tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        UserAccount created = UserAccount.create("carol", encoder.encode("the current password"), Instant.EPOCH);
        // Not even an expired password asks for a change: the source keeps it.
        created.requirePasswordChange();
        long account = TenantContext.callInTenant(tenant, () -> {
            IdentitySource source = IdentitySource.create("corp", IdentitySourceType.LDAP);
            source.configure("Corporate LDAP", true, true, "{}", null);
            long sourceId = sources.save(source).requireId();
            long id = accounts.save(created).requireId();
            links.save(ExternalIdentity.of(id, sourceId, "uuid-c"));
            return id;
        });

        AccountProfile profile = TenantContext.callInTenant(tenant, () -> profiles.find(account)).orElseThrow();
        assertThat(profile.identitySource()).isEqualTo("Corporate LDAP");
        assertThat(profile.passwordChangeRequired()).isFalse();
        assertThatThrownBy(() -> TenantContext.runInTenant(tenant, () -> profiles.changePassword(account, "the current password",
                "a brand new password here")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_MANAGED_EXTERNALLY));
    }

    private static ErrorCode codeOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void usersChangeTheirNameAndAddress()
    {
        long tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long account = TenantContext.callInTenant(tenant, () -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH)
                .withDisplayName("Alice")).requireId());

        AccountProfile updated = TenantContext.callInTenant(tenant, () -> profiles.update(account, " 爱丽丝 ", "alice@example.org"));
        assertThat(updated.displayName()).isEqualTo("爱丽丝");
        assertThat(updated.email()).isEqualTo("alice@example.org");

        AccountProfile cleared = TenantContext.callInTenant(tenant, () -> profiles.update(account, "", " "));
        assertThat(cleared.displayName()).isNull();
        assertThat(cleared.email()).isNull();

        assertThatThrownBy(() -> TenantContext.callInTenant(tenant, () -> profiles.update(account, null, "not-an-address")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> TenantContext.callInTenant(tenant + 1, () -> profiles.update(account, null, null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
    }

    @Test
    void usersChangeTheirPasswordAfterConfirmingTheCurrentOne()
    {
        long tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        UserAccount created = UserAccount.create("alice", encoder.encode("the current password"), Instant.EPOCH);
        created.requirePasswordChange();
        long account = TenantContext.callInTenant(tenant, () -> accounts.save(created).requireId());

        assertThatThrownBy(() -> TenantContext.runInTenant(tenant, () -> profiles.changePassword(account, "wrong",
                "a brand new password")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_INCORRECT));
        TenantContext.runInTenant(tenant, () -> profiles.changePassword(account, "the current password",
                "a brand new password"));

        UserAccount changed = TenantContext.callInTenant(tenant, () -> accounts.findById(account).orElseThrow());
        assertThat(encoder.matches("a brand new password", changed.getPasswordHash())).isTrue();
        assertThat(changed.isMustChangePassword()).isFalse();
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getTenantId, AuditEvent::getActorId,
                AuditEvent::getActorName).containsExactly(tuple(AuditAction.PASSWORD_CHANGED,
                tenant, account, "alice"));
    }
}
