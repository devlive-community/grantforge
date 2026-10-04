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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Authenticators of accounts. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MfaFactorRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private MfaFactorRepository factors;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            factors.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(long tenant, Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status -> action.get()));
    }

    @Test
    void keepsOneAuthenticatorPerAccountWithinItsTenant()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        long alice = inTenant(acme, () -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH))).requireId();
        inTenant(acme, () -> factors.save(MfaFactor.enroll(alice, "sealed")));

        assertThat(inTenant(acme, () -> factors.findByAccountId(alice))).get().extracting(MfaFactor::getSecret).isEqualTo("sealed");
        assertThat(inTenant(globex, () -> factors.findByAccountId(alice))).isEmpty();
        assertThatThrownBy(() -> inTenant(acme, () -> factors.saveAndFlush(MfaFactor.enroll(alice, "again"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Deleting the account deletes its authenticator.
        TenantContext.callAsSystem(() -> {
            accounts.deleteAllInBatch();
            return null;
        });
        assertThat(TenantContext.callAsSystem(() -> factors.count())).isZero();
    }
}
