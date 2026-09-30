// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.config;

import org.devlive.grantforge.persistence.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Registers GrantForge's Hibernate settings, currently tenant resolution for {@code @TenantId} filtering.
 *
 * <p>Listed in {@code AutoConfiguration.imports} and in the JPA test slice imports, so applications and
 * {@code @DataJpaTest} tests of every module get the same tenant isolation.
 */
@AutoConfiguration
public class PersistenceAutoConfiguration
{
    /**
     * Plugs {@link TenantIdentifierResolver} into Hibernate.
     *
     * @return the customizer
     */
    @Bean
    public HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer()
    {
        TenantIdentifierResolver resolver = new TenantIdentifierResolver();
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
