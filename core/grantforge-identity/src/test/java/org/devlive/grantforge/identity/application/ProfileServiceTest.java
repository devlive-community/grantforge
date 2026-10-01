// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class, ProfileService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfileServiceTest
{
    @Autowired
    private ProfileService profiles;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void describesTheAccountWithItsTenant()
    {
        long tenant = tenants.save(Tenant.create("acme", "Acme Corp")).requireId();
        UserAccount created = UserAccount.create("alice", "h", Instant.EPOCH).withDisplayName("Alice").markSystemAccount();
        created.requirePasswordChange();
        long account = TenantContext.callInTenant(tenant, () -> accounts.save(created).requireId());

        assertThat(TenantContext.callInTenant(tenant, () -> profiles.find(account))).get().isEqualTo(new AccountProfile(
                account, "alice", "Alice", null, "acme", "Acme Corp", true, true, null));
        // Another tenant cannot see the account.
        assertThat(TenantContext.callInTenant(tenant + 1, () -> profiles.find(account))).isEmpty();
    }
}
