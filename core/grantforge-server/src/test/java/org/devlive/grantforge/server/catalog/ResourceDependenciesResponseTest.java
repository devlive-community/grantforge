// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.DependencyView;
import org.devlive.grantforge.authz.application.ResourceDependencies;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceDependenciesResponseTest
{
    @Test
    void convertsBothDirections()
    {
        DependencyView view = new DependencyView(1, 2, 3, DependencyKind.REQUIRED, DependencySource.MANUAL);

        ResourceDependenciesResponse response = ResourceDependenciesResponse.from(new ResourceDependencies(List.of(view), List.of()));

        assertThat(response.requires()).containsExactly(DependencyResponse.from(view));
        assertThat(response.requiredBy()).isEmpty();
    }
}
