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
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrgMemberRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            members.deleteAllInBatch();
            accounts.deleteAllInBatch();
            units.deleteAllInBatch();
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
    void listsReplacesAndProtectsMemberships()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long hq = inTenant(() -> units.save(OrgUnit.create(null, "hq", "HQ", 0))).requireId();
        long lab = inTenant(() -> units.save(OrgUnit.create(null, "lab", "Lab", 1))).requireId();
        long alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH))).requireId();
        inTenant(() -> members.save(OrgMember.of(alice, lab, false)));
        inTenant(() -> members.save(OrgMember.of(alice, hq, true)));

        assertThat(inTenant(() -> members.findByAccount(alice))).extracting(OrgMember::getOrgUnitId).containsExactly(hq, lab);
        assertThat(inTenant(() -> members.existsByOrgUnitId(lab))).isTrue();
        assertThat(inTenant(() -> members.findByAccountIdIn(List.of(alice, -1L)))).hasSize(2);
        assertThatThrownBy(() -> inTenant(() -> members.save(OrgMember.of(alice, hq, false))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inTenant(() -> members.deleteByAccount(alice))).isEqualTo(2);
        assertThat(inTenant(() -> members.existsByOrgUnitId(lab))).isFalse();
    }
}
