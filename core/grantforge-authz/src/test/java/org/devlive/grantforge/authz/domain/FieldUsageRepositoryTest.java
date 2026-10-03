// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FieldUsageRepositoryTest
{
    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private FieldUsageRepository usages;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        usages.deleteAllInBatch();
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
    }

    @Test
    void listsWhereAFieldAppearsByPathAndKeepsEachOnce()
    {
        long app = applications.save(Application.create("console", "Console", null)).requireId();
        Resource entity = resources.save(Resource.create(app, null, ResourceType.DATA_ENTITY, "entity:user", CatalogTestData.details("Users"), 0));
        Resource email = resources.save(Resource.create(app, entity, ResourceType.FIELD, "entity:user.email", CatalogTestData.details("E-mail"), 0));
        usages.save(FieldUsage.of(email, "PUT", "/api/v1/users/{id}", FieldDirection.WRITE));
        usages.save(FieldUsage.of(email, "GET", "/api/v1/users", FieldDirection.READ));
        usages.save(FieldUsage.of(email, "PUT", "/api/v1/users/{id}", FieldDirection.READ));

        assertThat(usages.findByResourceIdOrderByPathPatternAscHttpMethodAscDirectionAsc(email.requireId()))
                .extracting(FieldUsage::getHttpMethod, FieldUsage::getPathPattern, FieldUsage::getDirection).containsExactly(
                        tuple("GET", "/api/v1/users", FieldDirection.READ),
                        tuple("PUT", "/api/v1/users/{id}", FieldDirection.READ),
                        tuple("PUT", "/api/v1/users/{id}", FieldDirection.WRITE));
        assertThat(usages.findByResourceIdOrderByPathPatternAscHttpMethodAscDirectionAsc(entity.requireId())).isEmpty();
        assertThatThrownBy(() -> usages.saveAndFlush(FieldUsage.of(email, "GET", "/api/v1/users", FieldDirection.READ)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
