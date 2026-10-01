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

/** Members of user groups. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupMemberRepositoryTest
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
    void listsFindsAndRemovesMembers()
    {
        long ops = inTenant(() -> groups.save(UserGroup.create("ops", "Ops", null))).requireId();
        inTenant(() -> members.saveAll(List.of(GroupMember.of(ops, alice), GroupMember.of(ops, bob))));

        assertThat(inTenant(() -> members.findMembers(ops, "%", PageRequest.of(0, 10))).getContent())
                .extracting(GroupMemberRow::username).containsExactly("alice", "bob");
        assertThat(inTenant(() -> members.findMembers(ops, "%爱丽%", PageRequest.of(0, 10))).getContent())
                .extracting(GroupMemberRow::accountId).containsExactly(alice);
        assertThat(inTenant(() -> members.findMembers(ops, "%acme.io%", PageRequest.of(0, 10))).getTotalElements()).isOne();
        assertThat(inTenant(() -> members.findMemberIds(ops, List.of(alice, -1L)))).containsExactly(alice);
        assertThat(inTenant(() -> members.findByAccountId(bob))).extracting(GroupMember::getGroupId).containsExactly(ops);
        assertThatThrownBy(() -> inTenant(() -> members.save(GroupMember.of(ops, alice))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inTenant(() -> members.removeMembers(ops, List.of(alice)))).isOne();
        assertThat(inTenant(() -> members.removeAll(ops))).isOne();
    }
}
