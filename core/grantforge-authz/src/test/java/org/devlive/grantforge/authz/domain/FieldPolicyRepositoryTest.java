// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
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

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FieldPolicyRepositoryTest
{
    @Autowired
    private FieldPolicyRepository policies;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status -> action.get()));
    }

    private static FieldPolicy policy(long role, String field)
    {
        return FieldPolicy.create(role, "user", field, FieldReadMode.HIDDEN, null, FieldWriteMode.EDITABLE);
    }

    @Test
    void keepsOnePolicyPerRoleAndFieldWithinTheTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long auditors = inTenant(() -> roles.save(Role.create("auditors", "Auditors", null)).requireId());
        long readers = inTenant(() -> roles.save(Role.create("readers", "Readers", null)).requireId());
        inTenant(() -> policies.saveAll(List.of(policy(auditors, "phone"), policy(auditors, "email"), policy(readers, "email"))));

        assertThat(inTenant(() -> policies.findByRoleIdOrderByEntityCodeAscFieldCodeAsc(auditors))).extracting(FieldPolicy::getFieldCode)
                .containsExactly("email", "phone");
        assertThat(inTenant(() -> policies.findByRoleIdIn(List.of(auditors, readers)))).hasSize(3);
        assertThat(TenantContext.callInTenant(tenant + 1, () -> policies.findByRoleIdIn(List.of(auditors)))).isEmpty();
        assertThatThrownBy(() -> inTenant(() -> policies.saveAndFlush(policy(readers, "email"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(inTenant(() -> policies.removeRole(auditors))).isEqualTo(2);
        assertThat(inTenant(() -> policies.findAll())).hasSize(1);
    }
}
