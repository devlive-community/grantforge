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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Recovery codes of accounts. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MfaRecoveryCodeRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private MfaRecoveryCodeRepository codes;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            codes.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status -> action.get()));
    }

    @Test
    void listsAndDeletesTheCodesOfOneAccount()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH))).requireId();
        long bob = inTenant(() -> accounts.save(UserAccount.create("bob", "h", Instant.EPOCH))).requireId();
        inTenant(() -> codes.save(MfaRecoveryCode.of(alice, "a1")));
        inTenant(() -> codes.save(MfaRecoveryCode.of(alice, "a2")));
        inTenant(() -> codes.save(MfaRecoveryCode.of(bob, "b1")));

        assertThat(inTenant(() -> codes.findByAccountId(alice))).extracting(MfaRecoveryCode::getCodeHash).containsExactlyInAnyOrder("a1", "a2");
        assertThat(inTenant(() -> codes.deleteByAccount(alice))).isEqualTo(2);

        assertThat(inTenant(() -> codes.findByAccountId(alice))).isEmpty();
        assertThat(inTenant(() -> codes.findByAccountId(bob))).hasSize(1);
    }
}
