// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FieldUsageTest
{
    private static Resource stored(ResourceType type, String code)
    {
        Resource entity = Resource.create(1L, null, ResourceType.DATA_ENTITY, "entity:user", CatalogTestData.details("Users"), 0);
        Resource resource = Resource.create(1L, type == ResourceType.FIELD ? entity : null, type, code, CatalogTestData.details(code), 0);
        ReflectionTestUtils.setField(resource, "id", 7L);
        return resource;
    }

    @Test
    void notesWhereAFieldAppears()
    {
        FieldUsage usage = FieldUsage.of(stored(ResourceType.FIELD, "entity:user.email"), "get", "/api/v1/users", FieldDirection.READ);

        assertThat(usage).extracting(FieldUsage::getResourceId, FieldUsage::getHttpMethod, FieldUsage::getPathPattern,
                FieldUsage::getDirection).containsExactly(7L, "GET", "/api/v1/users", FieldDirection.READ);
        assertThatIllegalArgumentException().isThrownBy(() -> FieldUsage.of(stored(ResourceType.API, "api"), "GET", "/api/v1/x",
                FieldDirection.WRITE)).withMessageContaining("no field");
    }
}
