// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.persistence.config.PersistenceAutoConfiguration;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PersistenceAutoConfiguration.class, DataEntities.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataEntitiesTest
{
    @Autowired
    private DataEntities directory;

    @Autowired
    private ApplicationEntityRepository declared;

    @Autowired
    private ApplicationRepository applications;

    @AfterEach
    void deleteRows()
    {
        declared.deleteAll();
        applications.deleteAllInBatch();
    }

    @Test
    void findsTheConsolesEntitiesAndThoseApplicationsDeclared()
    {
        long shop = applications.save(Application.create("shop", "Shop", null)).requireId();
        ApplicationEntity order = ApplicationEntity.create(shop, "shop:order");
        order.describe("Orders", true, false, List.of(new DataField("total", "Total", DataFieldType.NUMBER, List.of())));
        declared.save(order);

        SecuredEntityDefinition found = directory.find("shop:order").orElseThrow();
        assertThat(DataEntities.ofAnApplication(found)).isTrue();
        assertThat(found.name()).isEqualTo("Orders");
        // Own rows and conditions apply; departments do not, as rows have none.
        assertThat(found.scopes()).contains(DataScope.SELF, DataScope.CONDITION, DataScope.TENANT).doesNotContain(DataScope.ORG);
        assertThat(directory.find("user")).hasValueSatisfying(user -> assertThat(DataEntities.ofAnApplication(user)).isFalse());
        assertThat(directory.find("nothing")).isEmpty();
        assertThat(directory.find("shop:nothing")).isEmpty();
        assertThat(directory.all()).containsKeys("user", "shop:order");
        assertThat(directory.console()).extracting(SecuredEntityDefinition::code).contains("user").doesNotContain("shop:order");
        assertThat(directory.ofApplication(shop)).extracting(SecuredEntityDefinition::code).containsExactly("shop:order");
    }
}
