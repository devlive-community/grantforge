// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResourceRepositoryTest
{
    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long app;
    private long other;

    @BeforeEach
    void createApplications()
    {
        app = applications.save(Application.create("console", "Console", null)).requireId();
        other = applications.save(Application.create("crm", "CRM", null)).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
    }

    private Resource save(long application, @Nullable Resource parent, ResourceType type, String code, int order)
    {
        return resources.save(Resource.create(application, parent, type, code, CatalogTestData.details(code), order));
    }

    @Test
    void readsTreesLevelByLevelPerApplication()
    {
        Resource system = save(app, null, ResourceType.MODULE, "system", 1);
        save(app, null, ResourceType.MODULE, "audit", 0);
        Resource users = save(app, system, ResourceType.PAGE, "users", 1);
        save(app, system, ResourceType.PAGE, "groups", 0);
        save(app, users, ResourceType.ACTION, "users.export", 0);
        save(other, null, ResourceType.MODULE, "crm", 0);

        assertThat(resources.findTree(app)).extracting(Resource::getCode)
                .containsExactly("audit", "system", "groups", "users", "users.export");
        assertThat(resources.findChildren(app, null)).extracting(Resource::getCode).containsExactly("audit", "system");
        assertThat(resources.findChildren(app, system.requireId())).extracting(Resource::getCode).containsExactly("groups", "users");
        assertThat(resources.findByApplicationIdAndCode(app, "users")).map(Resource::requireId).contains(users.requireId());
        assertThat(resources.findByApplicationIdAndCode(other, "users")).isEmpty();
        assertThat(resources.existsByParentId(system.requireId())).isTrue();
        assertThat(resources.existsByParentId(users.requireId())).isTrue();
        assertThat(resources.existsByApplicationId(other)).isTrue();
        assertThat(resources.countByApplication()).containsExactlyInAnyOrder(new ResourceCount(app, 5), new ResourceCount(other, 1));
        assertThat(resources.maxDepthBelow(system.getPath() + "%")).isEqualTo(2);
    }

    @Test
    void codesAreUniquePerApplication()
    {
        save(app, null, ResourceType.MODULE, "system", 0);
        save(other, null, ResourceType.MODULE, "system", 0);

        assertThatThrownBy(() -> resources.saveAndFlush(Resource.create(app, null, ResourceType.API, "system",
                CatalogTestData.details("Again"), 1))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void subtreesMoveByRewritingTheirPathPrefix()
    {
        Resource system = save(app, null, ResourceType.MODULE, "system", 0);
        Resource menu = save(app, system, ResourceType.MENU, "menu", 0);
        Resource page = save(app, menu, ResourceType.PAGE, "page", 0);
        Resource button = save(app, page, ResourceType.ACTION, "button", 0);
        Resource target = save(app, null, ResourceType.MODULE, "target", 1);

        String oldPrefix = menu.getPath();
        String newPrefix = target.getPath() + menu.requireId() + "/";
        int moved = new TransactionTemplate(transactionManager).execute(status -> {
            resources.reparent(menu.requireId(), target.requireId());
            return resources.moveSubtree(oldPrefix, oldPrefix + "%", newPrefix, oldPrefix.length() + 1, 0);
        });

        assertThat(moved).isEqualTo(3);
        Resource reloaded = resources.findById(button.requireId()).orElseThrow();
        assertThat(reloaded.getPath()).isEqualTo(newPrefix + page.requireId() + "/" + button.requireId() + "/");
        assertThat(resources.findById(menu.requireId()).orElseThrow().getParentId()).isEqualTo(target.requireId());
        assertThat(resources.findById(system.requireId()).orElseThrow().getPath()).isEqualTo(system.getPath());
    }
}
