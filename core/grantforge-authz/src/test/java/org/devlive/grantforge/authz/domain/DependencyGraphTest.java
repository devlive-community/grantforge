// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphTest
{
    private static ResourceDependency edge(Resource from, Resource to, DependencyKind kind)
    {
        return ResourceDependency.create(from, to, kind, DependencySource.MANUAL);
    }

    @Test
    void followsRequiredDependenciesTransitivelyAndDetectsCycles()
    {
        Resource list = ResourceDependencyTest.resource(1, ResourceType.PAGE, "list");
        Resource edit = ResourceDependencyTest.resource(1, ResourceType.ACTION, "edit");
        Resource view = ResourceDependencyTest.resource(1, ResourceType.ACTION, "view");
        Resource read = ResourceDependencyTest.resource(1, ResourceType.API, "api:read");
        Resource update = ResourceDependencyTest.resource(1, ResourceType.API, "api:update");
        Resource roles = ResourceDependencyTest.resource(1, ResourceType.API, "api:roles");
        DependencyGraph graph = new DependencyGraph(List.of(
                edge(list, read, DependencyKind.REQUIRED),
                edge(edit, view, DependencyKind.REQUIRED),
                edge(edit, update, DependencyKind.REQUIRED),
                edge(view, read, DependencyKind.REQUIRED),
                edge(edit, roles, DependencyKind.OPTIONAL)));

        assertThat(graph.requiredBy(List.of(edit.requireId()))).containsExactly(view.requireId(), update.requireId(), read.requireId());
        assertThat(graph.requiredBy(List.of(list.requireId(), view.requireId()))).containsExactly(read.requireId());
        // A starting point counts when another one requires it.
        assertThat(graph.requiredBy(List.of(edit.requireId(), view.requireId()))).contains(view.requireId());
        assertThat(graph.requiredBy(List.of(read.requireId()))).isEmpty();

        assertThat(graph.wouldCycle(view.requireId(), edit.requireId())).isTrue();
        // Optional dependencies count for cycles too.
        assertThat(graph.wouldCycle(roles.requireId(), edit.requireId())).isTrue();
        assertThat(graph.wouldCycle(list.requireId(), list.requireId())).isTrue();
        assertThat(graph.wouldCycle(list.requireId(), edit.requireId())).isFalse();
        assertThat(new DependencyGraph(List.of()).wouldCycle(1, 2)).isFalse();
    }
}
