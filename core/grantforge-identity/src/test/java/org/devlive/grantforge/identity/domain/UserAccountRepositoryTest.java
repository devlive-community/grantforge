// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Each tenant's work runs in its own transaction started inside the binding, as in the application. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserAccountRepositoryTest
{
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    private long first;
    private long second;

    @BeforeEach
    void createTenants()
    {
        first = tenants.save(Tenant.create("first", "First")).requireId();
        second = tenants.save(Tenant.create("second", "Second")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.runInTenant(first, accounts::deleteAllInBatch);
        TenantContext.runInTenant(second, accounts::deleteAllInBatch);
        tenants.deleteAllInBatch();
    }

    @Test
    void accountsAreVisibleOnlyInTheirTenant()
    {
        TenantContext.runInTenant(first, () -> accounts.save(UserAccount.create("Alice", "h", NOW)));

        assertThat(TenantContext.callInTenant(first, () -> accounts.findByUsernameNorm("alice"))).isPresent();
        assertThat(TenantContext.callInTenant(second, () -> accounts.findByUsernameNorm("alice"))).isEmpty();
        assertThat(TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("alice")))
                .get().extracting(UserAccount::getTenantId).isEqualTo(first);
    }

    @Test
    void countsAccountsPerTenantInSystemContext()
    {
        TenantContext.runInTenant(first, () -> {
            accounts.save(UserAccount.create("alice", "h", NOW));
            accounts.save(UserAccount.create("bob", "h", NOW));
        });
        TenantContext.runInTenant(second, () -> accounts.save(UserAccount.create("carol", "h", NOW)));

        assertThat(TenantContext.callAsSystem(() -> accounts.countByTenant(List.of(first, second, -1L))))
                .extracting(UserAccountRepository.TenantAccounts::getTenantId, UserAccountRepository.TenantAccounts::getAccounts)
                .containsExactlyInAnyOrder(tuple(first, 2L), tuple(second, 1L));
    }

    @Test
    void loginNamesAreUniqueAcrossTenants()
    {
        TenantContext.runInTenant(first, () -> accounts.saveAndFlush(UserAccount.create("alice", "h", NOW)));

        assertThatThrownBy(() -> TenantContext.runInTenant(second,
                () -> accounts.saveAndFlush(UserAccount.create("ALICE", "h", NOW))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stateRoundTrips()
    {
        UserAccount account = UserAccount.create("bob", "h", NOW).withDisplayName("鲍勃 🔐").markSystemAccount();
        account.recordSuccessfulLogin(NOW);
        TenantContext.runInTenant(first, () -> accounts.save(account));

        UserAccount loaded = TenantContext.callInTenant(first, () -> accounts.findByUsernameNorm("bob")).orElseThrow();

        assertThat(loaded.getDisplayName()).isEqualTo("鲍勃 🔐");
        assertThat(loaded.isSystemAccount()).isTrue();
        assertThat(loaded.getLastLoginAt()).isEqualTo(NOW);
        assertThat(loaded.getPasswordChangedAt()).isEqualTo(NOW);
    }
}
