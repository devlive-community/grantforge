// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @AfterEach
    void deleteRows()
    {
        tenants.deleteAllInBatch();
    }

    @Test
    void preassignedIdSurvivesSaving()
    {
        Tenant tenant = Tenant.create("acme", "Acme");
        Long id = tenant.getId();

        Tenant saved = tenants.saveAndFlush(tenant);

        assertThat(saved.requireId()).isEqualTo(id);
        assertThat(tenants.findById(saved.requireId())).get().extracting(Tenant::getName).isEqualTo("Acme");
    }

    @Test
    void codesAreUnique()
    {
        tenants.saveAndFlush(Tenant.create("acme", "One"));

        assertThatThrownBy(() -> tenants.saveAndFlush(Tenant.create("ACME", "Two")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
