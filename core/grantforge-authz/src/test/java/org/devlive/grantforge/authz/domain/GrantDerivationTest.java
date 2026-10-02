// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.authz.domain.GrantDerivation.Reason;
import org.devlive.grantforge.authz.domain.GrantDerivation.ResourceState;
import org.devlive.grantforge.authz.domain.GrantDerivation.State;
import org.devlive.grantforge.authz.domain.GrantDerivation.Via;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class GrantDerivationTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    private final List<Resource> tree = new ArrayList<>();
    private final Resource system = add(null, ResourceType.MODULE, "system");
    private final Resource users = add(system, ResourceType.PAGE, "users");
    private final Resource edit = add(users, ResourceType.ACTION, "users.edit");
    private final Resource view = add(users, ResourceType.ACTION, "users.view");
    private final Resource groups = add(system, ResourceType.PAGE, "groups");
    private final Resource apiModule = add(null, ResourceType.MODULE, "api");
    private final Resource read = add(apiModule, ResourceType.API, "api:user.read");
    private final Resource update = add(apiModule, ResourceType.API, "api:user.update");
    private final Resource roles = add(apiModule, ResourceType.API, "api:role.read");

    private Resource add(@Nullable Resource parent, ResourceType type, String code)
    {
        Resource resource = Resource.create(1, parent, type, code, CatalogTestData.details(code), tree.size());
        tree.add(resource);
        return resource;
    }

    private GrantDerivation derivation()
    {
        return new GrantDerivation(tree, new DependencyGraph(List.of(
                ResourceDependency.create(users, read, DependencyKind.REQUIRED, DependencySource.DECLARED),
                ResourceDependency.create(edit, update, DependencyKind.REQUIRED, DependencySource.DECLARED),
                ResourceDependency.create(edit, view, DependencyKind.REQUIRED, DependencySource.DECLARED),
                ResourceDependency.create(edit, roles, DependencyKind.OPTIONAL, DependencySource.MANUAL))));
    }

    private static RoleGrant grant(Resource resource, GrantEffect effect, @Nullable Instant expires)
    {
        return RoleGrant.create(9, resource, effect, expires, 1);
    }

    private static ResourceState of(Map<Long, ResourceState> states, Resource resource)
    {
        return requireNonNull(states.get(resource.requireId()), resource.getCode());
    }

    private static Reason because(Resource resource, Via via)
    {
        return new Reason(resource.requireId(), via);
    }

    @Test
    void allowedButtonsImplyTheirPageModuleAndRequiredDependencies()
    {
        Map<Long, ResourceState> states = derivation().derive(List.of(grant(edit, GrantEffect.ALLOW, null)), List.of(), NOW);

        assertThat(of(states, edit).state()).isEqualTo(State.ALLOWED);
        assertThat(of(states, edit).explicit()).isTrue();
        assertThat(of(states, edit).reasons()).isEmpty();
        assertThat(of(states, update).reasons()).containsExactly(because(edit, Via.DEPENDENCY));
        assertThat(of(states, view).state()).isEqualTo(State.IMPLIED);
        // The page and module are shown because buttons below them are usable.
        assertThat(of(states, users).reasons()).contains(because(edit, Via.ANCESTOR), because(view, Via.ANCESTOR));
        assertThat(of(states, system).state()).isEqualTo(State.IMPLIED);
        assertThat(of(states, apiModule).reasons()).contains(because(update, Via.ANCESTOR));
        // Optional dependencies grant nothing, and an implied page does not pull in its own dependencies.
        assertThat(states).doesNotContainKeys(roles.requireId(), read.requireId(), groups.requireId());
        assertThat(states.values()).allMatch(ResourceState::effective);
    }

    @Test
    void denialsWinAndReachEverythingBelow()
    {
        Map<Long, ResourceState> states = derivation().derive(List.of(grant(edit, GrantEffect.ALLOW, null),
                grant(users, GrantEffect.DENY, null), grant(update, GrantEffect.DENY, null)), List.of(), NOW);

        assertThat(of(states, users).state()).isEqualTo(State.DENIED);
        assertThat(of(states, users).explicit()).isTrue();
        assertThat(of(states, edit).state()).isEqualTo(State.DENIED);
        assertThat(of(states, edit).reasons()).containsExactly(because(users, Via.DENIAL));
        assertThat(of(states, edit).effective()).isFalse();
        assertThat(of(states, view).state()).isEqualTo(State.DENIED);
        assertThat(of(states, update).state()).isEqualTo(State.DENIED);
        // Nothing allowed remains, so nothing is implied.
        assertThat(states).doesNotContainKeys(system.requireId(), read.requireId());
    }

    @Test
    void expiredGrantsAndForeignResourcesAreIgnored()
    {
        Resource elsewhere = Resource.create(2, null, ResourceType.PAGE, "elsewhere", CatalogTestData.details("E"), 0);

        Map<Long, ResourceState> states = derivation().derive(List.of(grant(groups, GrantEffect.ALLOW, NOW),
                grant(users, GrantEffect.DENY, NOW.minusSeconds(1)), grant(elsewhere, GrantEffect.ALLOW, null),
                grant(view, GrantEffect.ALLOW, NOW.plusSeconds(1))), List.of(), NOW);

        assertThat(states).containsOnlyKeys(view.requireId(), users.requireId(), system.requireId());
        assertThat(of(states, users).state()).isEqualTo(State.IMPLIED);
    }

    @Test
    void systemRolesAllowWholeModulesAndTheirDependencies()
    {
        Map<Long, ResourceState> states = derivation().derive(List.of(), List.of(system.requireId(), 42L), NOW);

        assertThat(of(states, system).reasons()).contains(because(system, Via.SYSTEM_ROLE));
        assertThat(of(states, edit).reasons()).containsExactly(because(system, Via.SYSTEM_ROLE));
        assertThat(of(states, update).reasons()).containsExactly(because(edit, Via.DEPENDENCY));
        assertThat(of(states, read).state()).isEqualTo(State.IMPLIED);
        assertThat(of(states, apiModule).state()).isEqualTo(State.IMPLIED);
        assertThat(states).doesNotContainKey(roles.requireId());
    }
}
