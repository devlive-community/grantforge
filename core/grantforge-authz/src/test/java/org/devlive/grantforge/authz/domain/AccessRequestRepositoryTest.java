// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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

/** Access requests and requestable roles of tenants. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccessRequestRepositoryTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private AccessRequestRepository requests;

    @Autowired
    private RequestableRoleRepository requestable;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            requests.deleteAllInBatch();
            requestable.deleteAllInBatch();
            roles.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void findsRequestsByAccountStateAndEnd()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long alice = TenantContext.callInTenant(acme, () -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH)).requireId());
        long reports = TenantContext.callInTenant(acme, () -> roles.save(Role.create("reports", "Reports", null)).requireId());
        TenantContext.callInTenant(acme, () -> requestable.save(RequestableRole.of(reports, 30)));
        TenantContext.callInTenant(acme, () -> requests.save(AccessRequest.file(alice, reports, "first", 3)));
        AccessRequest granted = AccessRequest.file(alice, reports, "second", 3);
        granted.approve(1, NOW, null, 5, NOW);
        TenantContext.callInTenant(acme, () -> requests.save(granted));

        assertThat(TenantContext.callInTenant(acme, () -> requests.findByRequesterIdOrderByCreatedAtDescIdDesc(alice, Limit.of(10)))).hasSize(2);
        assertThat(TenantContext.callInTenant(acme, () -> requests.findByStatusInOrderByCreatedAtDescIdDesc(List.of(AccessRequestStatus.PENDING),
                Limit.of(10)))).extracting(AccessRequest::getReason).containsExactly("first");
        assertThat(TenantContext.callInTenant(acme, () -> requests.existsByRequesterIdAndRoleIdAndStatus(alice, reports, AccessRequestStatus.PENDING)))
                .isTrue();
        assertThat(TenantContext.callAsSystem(() -> requests.findByStatusAndValidUntilLessThanEqual(AccessRequestStatus.APPROVED, NOW))).hasSize(1);
        assertThat(TenantContext.callAsSystem(() -> requests.findByStatusAndValidUntilLessThanEqual(AccessRequestStatus.APPROVED, NOW.minusSeconds(1))))
                .isEmpty();
        assertThat(TenantContext.callInTenant(acme, () -> requests.countByStatus(AccessRequestStatus.PENDING))).isOne();
        assertThat(TenantContext.callInTenant(acme, () -> requestable.findByRoleId(reports))).get().extracting(RequestableRole::getMaxDays)
                .isEqualTo(30);
    }
}
