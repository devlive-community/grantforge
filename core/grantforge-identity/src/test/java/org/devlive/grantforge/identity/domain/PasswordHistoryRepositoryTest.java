// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordHistoryRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordHistoryRepository history;

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

    @Test
    void listsNewestFirstWithinTheTenant()
    {
        long tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long account = TenantContext.callInTenant(tenant,
                () -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH)).requireId());
        TenantContext.runInTenant(tenant, () -> {
            history.save(PasswordHistory.of(account, "first"));
            history.save(PasswordHistory.of(account, "second"));
            history.save(PasswordHistory.of(account, "third"));
        });

        List<String> recent = TenantContext.callInTenant(tenant, () -> history
                .findByAccountIdOrderByCreatedAtDescIdDesc(account, Limit.of(2)).stream()
                .map(PasswordHistory::getPasswordHash).toList());

        assertThat(recent).containsExactly("third", "second");
        assertThat(TenantContext.callInTenant(tenant, () -> history.findByAccountIdOrderByCreatedAtDescIdDesc(account)))
                .hasSize(3);
        assertThat(TenantContext.callInTenant(tenant + 1, () -> history.findByAccountIdOrderByCreatedAtDescIdDesc(account)))
                .isEmpty();
    }
}
