// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceDependenciesTest
{
    @Test
    void copiesBothLists()
    {
        List<DependencyView> requires = new ArrayList<>(List.of(new DependencyView(1, 2, 3, DependencyKind.REQUIRED,
                DependencySource.MANUAL)));
        ResourceDependencies around = new ResourceDependencies(requires, List.of());
        requires.clear();

        assertThat(around.requires()).hasSize(1);
        assertThat(around.requiredBy()).isEmpty();
    }
}
