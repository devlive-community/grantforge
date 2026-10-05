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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Separation-of-duties constraints of tenants. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SodConstraintRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private SodConstraintRepository constraints;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            constraints.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private SodConstraint constraint(String code, String name, boolean enabled)
    {
        SodConstraint constraint = SodConstraint.create(code);
        constraint.configure(name, null, 1, SodMode.ENFORCE, enabled);
        return constraint;
    }

    @Test
    void keepsCodesUniquePerTenant()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        TenantContext.callInTenant(acme, () -> constraints.save(constraint("payments", "Payments", true)));
        TenantContext.callInTenant(acme, () -> constraints.save(constraint("audits", "Audits", false)));
        TenantContext.callInTenant(globex, () -> constraints.save(constraint("payments", "Payments", true)));

        assertThat(TenantContext.callInTenant(acme, () -> constraints.findAllByOrderByNameAsc())).extracting(SodConstraint::getCode)
                .containsExactly("audits", "payments");
        assertThat(TenantContext.callInTenant(acme, () -> constraints.findByEnabledTrue())).extracting(SodConstraint::getCode)
                .containsExactly("payments");
        assertThat(TenantContext.callInTenant(acme, () -> constraints.findByCode("audits"))).isPresent();
        assertThat(TenantContext.callInTenant(globex, () -> constraints.findByCode("audits"))).isEmpty();
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> constraints.saveAndFlush(constraint("payments", "Again", true))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
