// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.config;

import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.BatchSettings;
import org.hibernate.cfg.FetchSettings;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.cfg.QuerySettings;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceAutoConfigurationTest
{
    @Test
    void appliesPortableDefaults()
    {
        Map<String, Object> properties = new HashMap<>();

        new PersistenceAutoConfiguration().portableHibernateDefaults().customize(properties);

        assertThat(properties)
                .containsEntry(MappingSettings.PREFERRED_INSTANT_JDBC_TYPE, "TIMESTAMP")
                .containsEntry(JdbcSettings.JDBC_TIME_ZONE, "UTC")
                .containsEntry(BatchSettings.STATEMENT_BATCH_SIZE, PersistenceAutoConfiguration.BATCH_SIZE)
                .containsEntry(BatchSettings.ORDER_INSERTS, true)
                .containsEntry(BatchSettings.ORDER_UPDATES, true)
                .containsEntry(FetchSettings.DEFAULT_BATCH_FETCH_SIZE, PersistenceAutoConfiguration.BATCH_FETCH_SIZE)
                .containsEntry(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true)
                .containsEntry(QuerySettings.IN_CLAUSE_PARAMETER_PADDING, true);
    }

    @Test
    void registersTheTenantIdentifierResolver()
    {
        Map<String, Object> properties = new HashMap<>();

        new PersistenceAutoConfiguration().tenantIdentifierResolverCustomizer().customize(properties);

        assertThat(properties.get(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER))
                .isInstanceOf(TenantIdentifierResolver.class);
    }

    @Test
    void letsEveryRowThroughWithoutDataPolicies()
    {
        assertThat(new PersistenceAutoConfiguration().rowScopes()).isSameAs(RowScopes.unrestricted());
    }
}
