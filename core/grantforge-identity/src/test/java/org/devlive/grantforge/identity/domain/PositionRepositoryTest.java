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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Positions with their holder counts. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PositionRepositoryTest
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

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
    }

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
    void readsPositionsWithTheirHolderCounts()
    {
        long alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH))).requireId();
        long cfo = inTenant(() -> positions.save(Position.create("cfo", "财务总监", null, 2))).requireId();
        long dev = inTenant(() -> positions.save(Position.create("dev", "Developer", null, 1))).requireId();
        inTenant(() -> holdings.save(AccountPosition.of(alice, cfo)));

        assertThat(inTenant(() -> positions.rows(List.of(cfo, dev)))).extracting(PositionRow::code, PositionRow::holders)
                .containsExactlyInAnyOrder(tuple("dev", 0L), tuple("cfo", 1L));
        assertThat(inTenant(() -> positions.rows(List.of())).isEmpty()).isTrue();
        assertThat(inTenant(() -> positions.findByCode("cfo"))).isPresent();
        assertThatThrownBy(() -> inTenant(() -> positions.save(Position.create("cfo", "Again", null, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(TenantContext.callInTenant(tenant + 1, () -> positions.rows(List.of(cfo)))).isEmpty();
        assertThat(inTenant(() -> holdings.findByAccountId(alice))).hasSize(1);
    }
}
