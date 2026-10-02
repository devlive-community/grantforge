// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.devlive.grantforge.authz.domain.GrantDerivation.ResourceState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laws every derivation obeys, checked on random trees, dependencies and grants: denials win and reach down,
 * usable resources can be reached and have what they require, and more allowances never take anything away.
 */
class GrantDerivationProperties
{
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    /** A random catalog: a tree of modules, pages and buttons plus APIs, required dependencies and grants. */
    record Case(List<Resource> tree, List<ResourceDependency> edges, List<RoleGrant> grants)
    {
    }

    @Provide
    Arbitrary<Case> cases()
    {
        return Combinators.combine(Arbitraries.integers().between(1, 40), Arbitraries.longs().between(0, Long.MAX_VALUE))
                .as((size, seed) -> build(size, new java.util.Random(seed)));
    }

    private static Case build(int size, java.util.Random random)
    {
        List<Resource> tree = new ArrayList<>();
        Resource module = Resource.create(1, null, ResourceType.MODULE, "m0", CatalogTestData.details("m0"), 0);
        tree.add(module);
        Resource apis = Resource.create(1, null, ResourceType.MODULE, "apis", CatalogTestData.details("apis"), 1);
        tree.add(apis);
        List<Resource> pages = new ArrayList<>();
        List<Resource> targets = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int kind = random.nextInt(3);
            Resource created;
            if (kind == 0 || pages.isEmpty()) {
                created = Resource.create(1, module, ResourceType.PAGE, "p" + i, CatalogTestData.details("p" + i), i);
                pages.add(created);
            }
            else if (kind == 1) {
                created = Resource.create(1, pages.get(random.nextInt(pages.size())), ResourceType.ACTION, "b" + i,
                        CatalogTestData.details("b" + i), i);
            }
            else {
                created = Resource.create(1, apis, ResourceType.API, "a" + i, CatalogTestData.details("a" + i), i);
            }
            tree.add(created);
            if (created.getType() != ResourceType.PAGE || random.nextBoolean()) {
                targets.add(created);
            }
        }
        List<Resource> dependents = tree.stream().filter(resource -> ResourceDependency.DEPENDENTS.contains(resource.getType())).toList();
        List<ResourceDependency> edges = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Resource from = dependents.get(random.nextInt(dependents.size()));
            Resource to = targets.get(random.nextInt(targets.size()));
            boolean acyclic = !new DependencyGraph(edges).wouldCycle(from.requireId(), to.requireId());
            if (acyclic && from != to && ResourceDependency.TARGETS.contains(to.getType())) {
                edges.add(ResourceDependency.create(from, to, random.nextBoolean() ? DependencyKind.REQUIRED : DependencyKind.OPTIONAL,
                        DependencySource.MANUAL));
            }
        }
        List<Resource> grantable = tree.stream().filter(resource -> RoleGrant.GRANTABLE.contains(resource.getType())).toList();
        List<RoleGrant> grants = new ArrayList<>();
        for (int i = 0; i < size / 2 + 1; i++) {
            Resource target = grantable.get(random.nextInt(grantable.size()));
            grants.add(RoleGrant.create(9, target, random.nextInt(4) == 0 ? GrantEffect.DENY : GrantEffect.ALLOW,
                    random.nextInt(8) == 0 ? NOW : null, 1));
        }
        return new Case(tree, edges, grants);
    }

    private static Map<Long, ResourceState> derive(Case example, List<RoleGrant> grants)
    {
        return new GrantDerivation(example.tree(), new DependencyGraph(example.edges())).derive(grants, List.of(), NOW);
    }

    private static Set<Long> usable(Map<Long, ResourceState> states)
    {
        return states.entrySet().stream().filter(entry -> entry.getValue().effective()).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Property(tries = 300)
    void deniedResourcesAndEverythingBelowThemAreNeverUsable(@ForAll("cases") Case example)
    {
        Map<Long, ResourceState> states = derive(example, example.grants());
        Map<Long, Resource> byId = example.tree().stream().collect(Collectors.toMap(Resource::requireId, resource -> resource));
        for (RoleGrant grant : example.grants()) {
            if (grant.getEffect() == GrantEffect.DENY && grant.appliesAt(NOW)) {
                Resource denied = requireNonNull(byId.get(grant.getResourceId()));
                example.tree().stream().filter(denied::contains).forEach(below ->
                        assertThat(states.get(below.requireId())).satisfies(state -> assertThat(state.effective()).isFalse()));
            }
        }
    }

    @Property(tries = 300)
    void usableResourcesHaveTheirAncestorsAndRequiredDependencies(@ForAll("cases") Case example)
    {
        Map<Long, ResourceState> states = derive(example, example.grants());
        Set<Long> usable = usable(states);
        Map<Long, Resource> byId = example.tree().stream().collect(Collectors.toMap(Resource::requireId, resource -> resource));
        for (long id : usable) {
            Long parent = requireNonNull(byId.get(id)).getParentId();
            if (parent != null) {
                ResourceState above = states.get(parent);
                assertThat(above).as("parent of a usable resource").isNotNull();
                assertThat(above != null && above.effective()).as("parent of a usable resource").isTrue();
            }
        }
        for (ResourceDependency edge : example.edges()) {
            boolean explicitlyAllowed = states.containsKey(edge.getResourceId()) && states.get(edge.getResourceId()).explicit()
                    && states.get(edge.getResourceId()).effective();
            if (explicitlyAllowed && edge.getKind() == DependencyKind.REQUIRED && states.containsKey(edge.getDependsOnId())) {
                assertThat(states.get(edge.getDependsOnId()).state()).isIn(GrantDerivation.State.values());
            }
            if (explicitlyAllowed && edge.getKind() == DependencyKind.REQUIRED) {
                assertThat(states).as("required dependency is decided").containsKey(edge.getDependsOnId());
            }
        }
    }

    @Property(tries = 300)
    void moreAllowancesNeverTakeAnythingAwayAndMoreDenialsNeverAddAnything(@ForAll("cases") Case example)
    {
        Set<Long> before = usable(derive(example, example.grants()));
        List<RoleGrant> allowances = example.grants().stream().filter(grant -> grant.getEffect() == GrantEffect.ALLOW).toList();
        List<RoleGrant> denials = example.grants().stream().filter(grant -> grant.getEffect() == GrantEffect.DENY).toList();
        List<RoleGrant> moreAllowed = new ArrayList<>(example.grants());
        example.tree().stream().filter(resource -> RoleGrant.GRANTABLE.contains(resource.getType())).findFirst()
                .ifPresent(resource -> moreAllowed.add(RoleGrant.create(9, resource, GrantEffect.ALLOW, null, 1)));
        assertThat(usable(derive(example, moreAllowed))).containsAll(before);

        List<RoleGrant> moreDenied = new ArrayList<>(allowances);
        moreDenied.addAll(denials);
        allowances.stream().findFirst().ifPresent(grant -> moreDenied.add(grant.copyTo(9, 1)));
        assertThat(usable(derive(example, allowances))).containsAll(usable(derive(example, example.grants())));
    }

    @Property(tries = 200)
    void theOrderOfGrantsDoesNotMatter(@ForAll("cases") Case example, @ForAll long seed)
    {
        List<RoleGrant> shuffled = new ArrayList<>(example.grants());
        Collections.shuffle(shuffled, new java.util.Random(seed));
        Map<Long, GrantDerivation.State> first = new HashMap<>();
        derive(example, example.grants()).forEach((id, state) -> first.put(id, state.state()));
        Map<Long, GrantDerivation.State> second = new HashMap<>();
        derive(example, shuffled).forEach((id, state) -> second.put(id, state.state()));
        assertThat(second).isEqualTo(first);
    }
}
