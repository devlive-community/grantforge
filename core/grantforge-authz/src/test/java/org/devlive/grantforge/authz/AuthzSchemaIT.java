// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceCount;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.identity.application.TenantService;
import org.devlive.grantforge.persistence.naming.SchemaNamingVerifier;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the authorization changelog against the database chosen by {@code -Dgrantforge.it.database} (H2 by
 * default): start-up proves Hibernate validates the migrated schema, and the tests cover what differs between
 * databases, such as rewriting path prefixes with {@code concat}/{@code substring}.
 */
@SpringBootTest(classes = TestAuthzApplication.class)
class AuthzSchemaIT
{
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // The identity services are not part of this module's test context.
    @MockitoBean
    private TenantService tenantService;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase()
    {
        DATABASE.close();
    }

    @AfterEach
    void deleteRows()
    {
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
    }

    @Test
    void resourcesRoundTripWithUnicodeFlagsAndLongCodes()
    {
        long app = applications.save(Application.create("console", "控制台", "说明").markBuiltin()).requireId();
        String code = "api:DELETE:/api/v1/applications/{id}/resources/" + "x".repeat(200);
        Resource saved = resources.save(Resource.create(app, null, ResourceType.API, code,
                new ResourceDetails("删除资源", "说明", null, false, false, DenyMode.DISABLE), 3).markBuiltin());

        Resource loaded = resources.findById(saved.requireId()).orElseThrow();
        assertThat(loaded.getCode()).isEqualTo(code);
        assertThat(loaded.getType()).isEqualTo(ResourceType.API);
        assertThat(loaded.getDetails()).isEqualTo(new ResourceDetails("删除资源", "说明", null, false, false, DenyMode.DISABLE));
        assertThat(loaded.isBuiltin()).isTrue();
        assertThat(applications.findByCode("console").orElseThrow().isBuiltin()).isTrue();
        assertThat(resources.countByApplication()).containsExactly(new ResourceCount(app, 1));
        assertThatThrownBy(() -> resources.saveAndFlush(Resource.create(app, null, ResourceType.API, code,
                CatalogTestData.details("Again"), 0))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void subtreesMoveByRewritingTheirPathPrefix()
    {
        long app = applications.save(Application.create("console", "Console", null)).requireId();
        Resource system = resources.save(Resource.create(app, null, ResourceType.MODULE, "system", CatalogTestData.details("S"), 0));
        Resource page = resources.save(Resource.create(app, system, ResourceType.PAGE, "page", CatalogTestData.details("P"), 0));
        Resource button = resources.save(Resource.create(app, page, ResourceType.ACTION, "button", CatalogTestData.details("B"), 0));
        Resource target = resources.save(Resource.create(app, null, ResourceType.MODULE, "target", CatalogTestData.details("T"), 1));

        String oldPrefix = page.getPath();
        String newPrefix = target.getPath() + page.requireId() + "/";
        Integer moved = new TransactionTemplate(transactionManager).execute(status -> {
            resources.reparent(page.requireId(), target.requireId());
            return resources.moveSubtree(oldPrefix, oldPrefix + "%", newPrefix, oldPrefix.length() + 1, 0);
        });

        assertThat(moved).isEqualTo(2);
        assertThat(resources.findById(button.requireId()).orElseThrow().getPath()).isEqualTo(newPrefix + button.requireId() + "/");
        assertThat(resources.maxDepthBelow(target.getPath() + "%")).isEqualTo(2);
    }

    @Test
    void mappedNamesStayPortable()
    {
        assertThat(SchemaNamingVerifier.verify(entityManagerFactory)).isEmpty();
    }
}
