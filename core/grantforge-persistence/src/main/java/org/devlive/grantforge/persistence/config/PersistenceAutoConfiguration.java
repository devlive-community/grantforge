// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.config;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.devlive.grantforge.persistence.authz.AuthorizationVersionSink;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.BatchSettings;
import org.hibernate.cfg.FetchSettings;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.cfg.QuerySettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Registers GrantForge's Hibernate settings: tenant resolution for {@code @TenantId} filtering and portable
 * defaults that behave the same on every supported database.
 *
 * <p>Listed in {@code AutoConfiguration.imports} and in the JPA test slice imports, so applications and
 * {@code @DataJpaTest} tests of every module get the same tenant isolation.
 */
@AutoConfiguration
public class PersistenceAutoConfiguration
{
    /** Rows written per JDBC batch; requires application-generated IDs (TSID), which GrantForge uses. */
    static final int BATCH_SIZE = 50;
    /** Lazy associations of up to this many entities are loaded in one query instead of N+1 queries. */
    static final int BATCH_FETCH_SIZE = 64;

    /**
     * Portable Hibernate defaults.
     *
     * <ul>
     *   <li>{@link java.time.Instant} is stored as a plain timestamp in UTC. Hibernate's default uses
     *       time-zone-aware column types, which MySQL, MariaDB and SQL Server handle differently.</li>
     *   <li>Inserts and updates are batched and ordered by entity so batches stay large.</li>
     *   <li>Lazy associations are fetched in batches, and paginating a collection fetch in memory is an
     *       error instead of a silent full-table load.</li>
     *   <li>IN-clause parameters are padded to powers of two so fewer distinct statements are prepared.</li>
     * </ul>
     *
     * @return the customizer
     */
    @Bean
    public HibernatePropertiesCustomizer portableHibernateDefaults()
    {
        return properties -> {
            properties.put(MappingSettings.PREFERRED_INSTANT_JDBC_TYPE, "TIMESTAMP");
            properties.put(JdbcSettings.JDBC_TIME_ZONE, "UTC");
            properties.put(BatchSettings.STATEMENT_BATCH_SIZE, BATCH_SIZE);
            properties.put(BatchSettings.ORDER_INSERTS, true);
            properties.put(BatchSettings.ORDER_UPDATES, true);
            properties.put(FetchSettings.DEFAULT_BATCH_FETCH_SIZE, BATCH_FETCH_SIZE);
            properties.put(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true);
            properties.put(QuerySettings.IN_CLAUSE_PARAMETER_PADDING, true);
        };
    }

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

    /**
     * Collects changes of data that permissions are worked out from, for the version sink if one is configured.
     *
     * @param sinks the sink, if any
     * @param factories the JPA entity manager factory
     * @return the collector
     */
    @Bean
    public AuthorizationChanges authorizationChanges(ObjectProvider<AuthorizationVersionSink> sinks,
            ObjectProvider<EntityManagerFactory> factories)
    {
        return new AuthorizationChanges(sinks, factories);
    }

    /**
     * Reads and checks the entities that declare themselves secured, so a declaration that does not fit stops the start.
     *
     * @param factories the JPA entity manager factory, if the application has one
     * @return the registry; empty without JPA
     */
    @Bean
    public SecuredEntities securedEntities(ObjectProvider<EntityManagerFactory> factories)
    {
        EntityManagerFactory factory = factories.getIfAvailable();
        return factory == null ? SecuredEntities.none() : SecuredEntities.of(factory);
    }

    /**
     * Lets every row through when nothing provides data scopes: without the authorization module no data policy can limit
     * rows. The authorization module's policy-based scopes replace this.
     *
     * @return scopes that cover every row
     */
    @Bean
    @ConditionalOnMissingBean
    public RowScopes rowScopes()
    {
        return RowScopes.unrestricted();
    }

    /**
     * Shows every field as it is when nothing provides field rules: without the authorization module no field policy can
     * hide or mask fields. The authorization module's policy-based rules replace this.
     *
     * @return rules that show every field
     */
    @Bean
    @ConditionalOnMissingBean
    public FieldRules fieldRules()
    {
        return FieldRules.open();
    }
}
