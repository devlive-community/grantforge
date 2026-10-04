// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationEntityRepositoryTest
{
    @Autowired
    private ApplicationEntityRepository entities;

    @Autowired
    private ApplicationRepository applications;

    @AfterEach
    void deleteRows()
    {
        entities.deleteAll();
        applications.deleteAllInBatch();
    }

    @Test
    void storesFieldsAndFindsEntitiesByApplicationAndCode()
    {
        long shop = applications.save(Application.create("shop", "Shop", null)).requireId();
        long crm = applications.save(Application.create("crm", "CRM", null)).requireId();
        entities.save(entity(shop, "shop:order"));
        entities.save(entity(shop, "shop:invoice"));
        entities.save(entity(crm, "crm:lead"));

        assertThat(entities.findByApplicationIdOrderByCode(shop)).extracting(ApplicationEntity::getCode)
                .containsExactly("shop:invoice", "shop:order");
        assertThat(entities.findAllByOrderByCode()).extracting(ApplicationEntity::getCode).containsExactly("crm:lead", "shop:invoice", "shop:order");
        assertThat(entities.findByCode("shop:order")).hasValueSatisfying(found ->
                assertThat(found.getFields()).extracting(DataField::code).containsExactly("status", "total"));
        assertThat(entities.existsByApplicationId(crm)).isTrue();
        assertThatThrownBy(() -> entities.saveAndFlush(entity(crm, "shop:order"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ApplicationEntity entity(long applicationId, String code)
    {
        ApplicationEntity entity = ApplicationEntity.create(applicationId, code);
        entity.describe(code, true, true, List.of(new DataField("status", "Status", DataFieldType.CHOICE, List.of("OPEN")),
                new DataField("total", "Total", DataFieldType.NUMBER, List.of())));
        return entity;
    }
}
