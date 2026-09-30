// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.config;

import org.devlive.grantforge.persistence.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.MultiTenancySettings;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceAutoConfigurationTest
{
    @Test
    void registersTheTenantIdentifierResolver()
    {
        Map<String, Object> properties = new HashMap<>();

        new PersistenceAutoConfiguration().tenantIdentifierResolverCustomizer().customize(properties);

        assertThat(properties.get(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER))
                .isInstanceOf(TenantIdentifierResolver.class);
    }
}
