// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
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

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleAssignmentRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long acme;
    private long auditors;
    private long buyers;

    @BeforeEach
    void createRoles()
    {
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        auditors = TenantContext.callInTenant(acme, () -> roles.save(Role.create("auditors", "Auditors", null)).requireId());
        buyers = TenantContext.callInTenant(acme, () -> roles.save(Role.create("buyers", "Buyers", null)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private void save(long role, SubjectType type, long subject)
    {
        TenantContext.runInTenant(acme, () -> assignments.save(RoleAssignment.create(role, type, subject, RoleAssignment.Terms.UNLIMITED)));
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(acme, () -> new TransactionTemplate(transactionManager).execute(status -> action.get()));
    }

    @Test
    void findsAndRemovesAssignmentsByRoleAndSubject()
    {
        save(auditors, SubjectType.USER, 1);
        save(auditors, SubjectType.GROUP, 2);
        save(buyers, SubjectType.USER, 1);

        assertThat(inTenant(() -> assignments.findByRole(auditors))).extracting(RoleAssignment::getSubjectType)
                .containsExactly(SubjectType.USER, SubjectType.GROUP);
        assertThat(inTenant(() -> assignments.findBySubjects(SubjectType.USER, List.of(1L, 9L)))).hasSize(2);
        assertThat(inTenant(() -> assignments.findByRoleIdAndSubjectTypeAndSubjectId(buyers, SubjectType.USER, 1))).isPresent();
        assertThatThrownBy(() -> save(buyers, SubjectType.USER, 1)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inTenant(() -> assignments.removeSubject(SubjectType.USER, 1))).isEqualTo(2);
        assertThat(inTenant(() -> assignments.removeRole(auditors))).isOne();
        assertThat(inTenant(() -> assignments.count())).isZero();
    }
}
