// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceDependencyTest
{
    static Resource resource(long application, ResourceType type, String code)
    {
        Resource parent = type == ResourceType.ACTION || type == ResourceType.TAB
                ? Resource.create(application, null, ResourceType.PAGE, code + ".page", CatalogTestData.details("P"), 0) : null;
        return Resource.create(application, parent, type, code, CatalogTestData.details(code), 0);
    }

    @Test
    void buttonsAndPagesDependOnApisPagesTabsAndButtons()
    {
        Resource export = resource(1, ResourceType.ACTION, "export");
        Resource api = resource(1, ResourceType.API, "api:system.user.export");

        ResourceDependency dependency = ResourceDependency.create(export, api, DependencyKind.REQUIRED, DependencySource.MANUAL);

        assertThat(dependency.getApplicationId()).isEqualTo(1);
        assertThat(dependency.getResourceId()).isEqualTo(export.requireId());
        assertThat(dependency.getDependsOnId()).isEqualTo(api.requireId());
        assertThat(dependency.getKind()).isEqualTo(DependencyKind.REQUIRED);
        assertThat(dependency.getSource()).isEqualTo(DependencySource.MANUAL);
        dependency.changeKind(DependencyKind.OPTIONAL);
        assertThat(dependency.getKind()).isEqualTo(DependencyKind.OPTIONAL);
        assertThat(ResourceDependency.create(resource(1, ResourceType.PAGE, "edit-page"), export, DependencyKind.OPTIONAL,
                DependencySource.DECLARED).getSource()).isEqualTo(DependencySource.DECLARED);
    }

    @Test
    void refusesOtherTypesItselfAndOtherApplications()
    {
        Resource page = resource(1, ResourceType.PAGE, "page");
        Resource api = resource(1, ResourceType.API, "api:x.y");

        assertThatThrownBy(() -> ResourceDependency.create(api, page, DependencyKind.REQUIRED, DependencySource.MANUAL))
                .hasMessage("API cannot depend on PAGE");
        assertThatThrownBy(() -> ResourceDependency.create(page, resource(1, ResourceType.MODULE, "m"), DependencyKind.REQUIRED,
                DependencySource.MANUAL)).hasMessage("PAGE cannot depend on MODULE");
        assertThatThrownBy(() -> ResourceDependency.create(page, page, DependencyKind.REQUIRED, DependencySource.MANUAL))
                .hasMessageContaining("itself");
        assertThatThrownBy(() -> ResourceDependency.create(page, resource(2, ResourceType.API, "api:x.y"), DependencyKind.REQUIRED,
                DependencySource.MANUAL)).hasMessageContaining("one application");
    }
}
