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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Positions held by accounts. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountPositionRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private AccountPositionRepository holdings;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            holdings.deleteAllInBatch();
            positions.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status ->
                action.get()));
    }

    @Test
    void listsAndRemovesHolders()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH).withDisplayName("爱丽丝"))).requireId();
        long bob = inTenant(() -> accounts.save(UserAccount.create("bob", "h", Instant.EPOCH))).requireId();
        long cfo = inTenant(() -> positions.save(Position.create("cfo", "CFO", null, 0))).requireId();
        long dev = inTenant(() -> positions.save(Position.create("dev", "Dev", null, 0))).requireId();
        inTenant(() -> holdings.save(AccountPosition.of(bob, cfo)));
        inTenant(() -> holdings.save(AccountPosition.of(alice, cfo)));
        inTenant(() -> holdings.save(AccountPosition.of(alice, dev)));

        assertThat(inTenant(() -> holdings.findHolders(cfo, PageRequest.of(0, 10))).getContent())
                .extracting(MemberRow::username).containsExactly("alice", "bob");
        assertThat(inTenant(() -> holdings.findByAccountIdIn(List.of(alice, bob)))).hasSize(3);
        assertThatThrownBy(() -> inTenant(() -> holdings.save(AccountPosition.of(alice, cfo))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(inTenant(() -> holdings.removeAllOf(alice))).isEqualTo(2);
        assertThat(inTenant(() -> holdings.removePosition(cfo))).isOne();
        assertThat(inTenant(() -> holdings.findHolders(cfo, PageRequest.of(0, 10))).getTotalElements()).isZero();
    }
}
