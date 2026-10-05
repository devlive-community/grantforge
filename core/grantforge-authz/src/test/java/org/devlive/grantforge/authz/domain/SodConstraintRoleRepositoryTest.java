// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Roles of separation-of-duties constraints. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SodConstraintRoleRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private SodConstraintRepository constraints;

    @Autowired
    private SodConstraintRoleRepository links;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            links.deleteAllInBatch();
            constraints.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void listsAndRemovesTheRolesOfConstraintsAndForgetsDeletedRoles()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long payer = TenantContext.callInTenant(acme, () -> roles.save(Role.create("payer", "Payer", null)).requireId());
        long approver = TenantContext.callInTenant(acme, () -> roles.save(Role.create("approver", "Approver", null)).requireId());
        long payments = TenantContext.callInTenant(acme, () -> {
            SodConstraint constraint = SodConstraint.create("payments");
            constraint.configure("Payments", null, 1, SodMode.ENFORCE, true);
            return constraints.save(constraint).requireId();
        });
        TenantContext.runInTenant(acme, () -> links.saveAll(List.of(SodConstraintRole.of(payments, payer), SodConstraintRole.of(payments, approver))));

        assertThat(TenantContext.callInTenant(acme, () -> links.findByConstraintIdIn(List.of(payments)))).hasSize(2);
        // Deleting a role takes it out of the constraints.
        TenantContext.runInTenant(acme, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                roles.deleteById(approver)));
        assertThat(TenantContext.callInTenant(acme, () -> links.findByConstraintIdIn(List.of(payments)))).extracting(SodConstraintRole::getRoleId)
                .containsExactly(payer);
        Integer removed = TenantContext.callInTenant(acme, () -> new TransactionTemplate(transactionManager).execute(status ->
                links.deleteByConstraint(payments)));
        assertThat(removed).isOne();
    }
}
