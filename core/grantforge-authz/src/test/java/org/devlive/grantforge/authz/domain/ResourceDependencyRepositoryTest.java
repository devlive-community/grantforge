// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResourceDependencyRepositoryTest
{
    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private ResourceDependencyRepository dependencies;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Resource page;
    private Resource button;
    private Resource api;

    @BeforeEach
    void createResources()
    {
        long app = applications.save(Application.create("console", "Console", null)).requireId();
        page = resources.save(Resource.create(app, null, ResourceType.PAGE, "users", CatalogTestData.details("Users"), 0));
        button = resources.save(Resource.create(app, page, ResourceType.ACTION, "export", CatalogTestData.details("Export"), 0));
        api = resources.save(Resource.create(app, null, ResourceType.API, "api:users", CatalogTestData.details("API"), 1));
    }

    @AfterEach
    void deleteRows()
    {
        dependencies.deleteAllInBatch();
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
    }

    @Test
    void findsDependenciesFromBothEndsAndKeepsPairsUnique()
    {
        dependencies.save(ResourceDependency.create(page, api, DependencyKind.REQUIRED, DependencySource.DECLARED));
        dependencies.save(ResourceDependency.create(button, api, DependencyKind.REQUIRED, DependencySource.MANUAL));
        dependencies.save(ResourceDependency.create(button, page, DependencyKind.OPTIONAL, DependencySource.MANUAL));

        assertThat(dependencies.findByApplicationId(page.getApplicationId())).hasSize(3);
        assertThat(dependencies.findByResourceId(button.requireId())).extracting(ResourceDependency::getDependsOnId)
                .containsExactlyInAnyOrder(api.requireId(), page.requireId());
        assertThat(dependencies.findByDependsOnId(api.requireId())).hasSize(2);
        assertThat(dependencies.existsByDependsOnId(page.requireId())).isTrue();
        assertThat(dependencies.existsByDependsOnId(button.requireId())).isFalse();
        assertThat(dependencies.findByResourceIdAndDependsOnId(page.requireId(), api.requireId()))
                .map(ResourceDependency::getSource).contains(DependencySource.DECLARED);
        assertThatThrownBy(() -> dependencies.saveAndFlush(ResourceDependency.create(page, api, DependencyKind.OPTIONAL,
                DependencySource.MANUAL))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
