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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Identity sources of tenants. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentitySourceRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private IdentitySourceRepository sources;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            sources.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private IdentitySource source(String code, IdentitySourceType type, boolean enabled)
    {
        IdentitySource source = IdentitySource.create(code, type);
        source.configure(code, enabled, true, "{}", null);
        return source;
    }

    @Test
    void findsSourcesWithinTheirTenantAndByTypeAcrossTenants()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        TenantContext.callInTenant(acme, () -> sources.save(source("corp", IdentitySourceType.LDAP, true)));
        TenantContext.callInTenant(acme, () -> sources.save(source("okta", IdentitySourceType.OIDC, true)));
        TenantContext.callInTenant(globex, () -> sources.save(source("old", IdentitySourceType.LDAP, false)));

        assertThat(TenantContext.callInTenant(acme, () -> sources.findAllByOrderByIdAsc())).extracting(IdentitySource::getCode)
                .containsExactly("corp", "okta");
        assertThat(TenantContext.callInTenant(globex, () -> sources.findByCode("corp"))).isEmpty();
        assertThat(TenantContext.callAsSystem(() -> sources.findByCode("corp"))).isPresent();
        assertThat(TenantContext.callAsSystem(() -> sources.findByTypeAndEnabledTrueOrderByIdAsc(IdentitySourceType.LDAP)))
                .extracting(IdentitySource::getCode).containsExactly("corp");
        // Codes are unique on the platform.
        assertThatThrownBy(() -> TenantContext.callInTenant(globex, () -> sources.saveAndFlush(source("corp", IdentitySourceType.LDAP, true))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
