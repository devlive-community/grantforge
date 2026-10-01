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
import static org.assertj.core.api.Assertions.tuple;

/** User groups with their member counts. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserGroupRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private UserGroupRepository groups;

    @Autowired
    private GroupMemberRepository members;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;
    private long alice;
    private long bob;

    @BeforeEach
    void createData()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH).withDisplayName("爱丽丝"))).requireId();
        bob = inTenant(() -> accounts.save(UserAccount.create("bob", "h", Instant.EPOCH).withEmail("bob@acme.io"))).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            members.deleteAllInBatch();
            groups.deleteAllInBatch();
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
    void searchesGroupsWithTheirMemberCounts()
    {
        long ops = inTenant(() -> groups.save(UserGroup.create("ops", "运维组", null))).requireId();
        inTenant(() -> groups.save(UserGroup.create("dev", "Developers", "写代码")));
        inTenant(() -> members.saveAll(List.of(GroupMember.of(ops, alice), GroupMember.of(ops, bob))));

        assertThat(inTenant(() -> groups.search("%", PageRequest.of(0, 10))).getContent())
                .extracting(GroupRow::code, GroupRow::members).containsExactly(tuple("dev", 0L), tuple("ops", 2L));
        assertThat(inTenant(() -> groups.search("%运维%", PageRequest.of(0, 10))).getTotalElements()).isOne();
        assertThat(inTenant(() -> groups.findByCode("dev"))).isPresent();
        assertThat(TenantContext.callInTenant(tenant + 1, () -> groups.search("%", PageRequest.of(0, 10)).getTotalElements()))
                .isZero();
        assertThatThrownBy(() -> inTenant(() -> groups.save(UserGroup.create("ops", "Again", null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
