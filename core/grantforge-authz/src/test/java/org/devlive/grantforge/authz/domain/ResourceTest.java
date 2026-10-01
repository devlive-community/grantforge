// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceTest
{
    private static final long APP = 1;

    @Test
    void pathsListTheAncestorsAndTheResourceItself()
    {
        Resource system = Resource.create(APP, null, ResourceType.MODULE, "system", CatalogTestData.details("System"), 0);
        Resource users = Resource.create(APP, system, ResourceType.PAGE, "system.user.list",
                new ResourceDetails(" Users ", " list ", "/admin/users", false, false, DenyMode.DISABLE), 2);
        Resource export = Resource.create(APP, users, ResourceType.ACTION, "system.user.btn.export", CatalogTestData.details("Export"), 0);

        assertThat(system.getPath()).isEqualTo("/" + system.requireId() + "/");
        assertThat(system.getParentId()).isNull();
        assertThat(users.getPath()).isEqualTo(system.getPath() + users.requireId() + "/");
        assertThat(users.getParentId()).isEqualTo(system.requireId());
        assertThat(users.getApplicationId()).isEqualTo(APP);
        assertThat(users.getType()).isEqualTo(ResourceType.PAGE);
        assertThat(users.getDepth()).isOne();
        assertThat(users.getSortOrder()).isEqualTo(2);
        assertThat(users.getDetails()).isEqualTo(new ResourceDetails("Users", "list", "/admin/users", false, false, DenyMode.DISABLE));
        assertThat(export.getDepth()).isEqualTo(2);
        assertThat(system.contains(export)).isTrue();
        assertThat(export.contains(system)).isFalse();
        assertThat(users.isBuiltin()).isFalse();
        assertThat(users.markBuiltin().isBuiltin()).isTrue();
        users.placeAt(5);
        assertThat(users.getSortOrder()).isEqualTo(5);
    }

    @Test
    void typesOnlySitWhereTheyBelong()
    {
        Resource page = Resource.create(APP, null, ResourceType.PAGE, "page", CatalogTestData.details("Page"), 0);
        Resource api = Resource.create(APP, null, ResourceType.API, "api:GET:/api/v1/users/{id}", CatalogTestData.details("Get user"), 0);

        assertThatThrownBy(() -> Resource.create(APP, null, ResourceType.ACTION, "button", CatalogTestData.details("Button"), 0))
                .hasMessage("ACTION cannot be placed below the top level");
        assertThatThrownBy(() -> Resource.create(APP, api, ResourceType.ACTION, "button", CatalogTestData.details("Button"), 0))
                .hasMessage("ACTION cannot be placed below API");
        assertThatThrownBy(() -> Resource.create(2, page, ResourceType.ACTION, "button", CatalogTestData.details("Button"), 0))
                .hasMessageContaining("another application");
        assertThat(api.canMoveBelow(page)).isFalse();
        assertThat(api.canMoveBelow(null)).isTrue();
    }

    @Test
    void nestsAtMostTheMaximumDepth()
    {
        Resource resource = Resource.create(APP, null, ResourceType.MODULE, "m0", CatalogTestData.details("M0"), 0);
        for (int level = 1; level <= Resource.MAX_DEPTH; level++) {
            resource = Resource.create(APP, resource, ResourceType.MODULE, "m" + level, CatalogTestData.details("M" + level), 0);
        }
        Resource deepest = resource;

        assertThatThrownBy(() -> Resource.create(APP, deepest, ResourceType.MODULE, "too-deep", CatalogTestData.details("Deep"), 0))
                .hasMessageContaining("at most 16 levels");
    }

    @Test
    void codesAndRoutesAreChecked()
    {
        Resource page = Resource.create(APP, null, ResourceType.PAGE, "page", CatalogTestData.details("Page"), 0);

        page.recode(" System.User:list ");
        assertThat(page.getCode()).isEqualTo("System.User:list");
        assertThatThrownBy(() -> page.recode("-leading")).hasMessageContaining("code must be");
        assertThatThrownBy(() -> page.recode("has space")).hasMessageContaining("code must be");
        assertThatThrownBy(() -> page.recode("x".repeat(Resource.CODE_MAX + 1))).hasMessageContaining("code must be");
        assertThatThrownBy(() -> page.update(new ResourceDetails("Page", null, "admin/users", true, true, DenyMode.HIDE)))
                .hasMessageContaining("route");
        assertThatThrownBy(() -> page.update(new ResourceDetails("Page", null, "/a b", true, true, DenyMode.HIDE)))
                .hasMessageContaining("route");
        page.update(new ResourceDetails("Page", null, " ", true, true, DenyMode.HIDE));
        assertThat(page.getDetails().route()).isNull();

        Resource api = Resource.create(APP, null, ResourceType.API, "api", CatalogTestData.details("API"), 0);
        assertThatThrownBy(() -> api.update(new ResourceDetails("API", null, "/api", true, true, DenyMode.HIDE)))
                .hasMessageContaining("only menus, pages and tabs");
        assertThatThrownBy(() -> api.update(new ResourceDetails(" ", null, null, true, true, DenyMode.HIDE)))
                .hasMessageContaining("name");
    }
}
