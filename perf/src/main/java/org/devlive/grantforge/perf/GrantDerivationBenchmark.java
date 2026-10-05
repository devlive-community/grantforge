// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.DependencyGraph;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * How long working out a role's grants takes in an application with a large resource tree (the in-memory part of a
 * snapshot): preparing the derivation for the tree, which every snapshot build does, and deriving the states of twenty
 * grants. The perf benchmark runs it through JMH's runner; it also starts from JMH's own main.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class GrantDerivationBenchmark
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final int PAGES_PER_MODULE = 100;
    private static final int ACTIONS_PER_PAGE = 9;
    private static final int GRANTS = 20;

    /** How many resources the application has. */
    @Param("100000")
    public int resources;

    // Replaced in setUp(), which JMH calls before measuring.
    private List<Resource> tree = List.of();
    private List<RoleGrant> grants = List.of();
    private DependencyGraph dependencies = new DependencyGraph(List.of());
    private GrantDerivation derivation = new GrantDerivation(List.of(), dependencies);

    /** Builds a tree of modules, pages and actions like the system benchmark seeds, and a role's grants in it. */
    @Setup(Level.Trial)
    public void setUp()
    {
        List<Resource> all = new ArrayList<>(resources);
        List<Resource> grantable = new ArrayList<>();
        int modules = Math.max(1, resources / (1 + PAGES_PER_MODULE * (1 + ACTIONS_PER_PAGE)));
        for (int module = 0; module < modules; module++) {
            Resource parent = Resource.create(1, null, ResourceType.MODULE, "m" + module, details("Module"), module);
            all.add(parent);
            for (int page = 0; page < PAGES_PER_MODULE; page++) {
                Resource screen = Resource.create(1, parent, ResourceType.PAGE, parent.getCode() + ".p" + page, details("Page"), page);
                all.add(screen);
                grantable.add(screen);
                for (int action = 0; action < ACTIONS_PER_PAGE; action++) {
                    Resource button = Resource.create(1, screen, ResourceType.ACTION, screen.getCode() + ".a" + action, details("Action"), action);
                    all.add(button);
                    grantable.add(button);
                }
            }
        }
        List<RoleGrant> chosen = new ArrayList<>();
        for (int index = 0; index < GRANTS; index++) {
            chosen.add(RoleGrant.create(7, grantable.get(index * 997 % grantable.size()), GrantEffect.ALLOW, null, 1));
        }
        tree = List.copyOf(all);
        grants = List.copyOf(chosen);
        derivation = new GrantDerivation(tree, dependencies);
    }

    /**
     * Prepares the derivation for the tree.
     *
     * @return the derivation, so the JIT keeps the work
     */
    @Benchmark
    public GrantDerivation prepare()
    {
        return new GrantDerivation(tree, dependencies);
    }

    /**
     * Derives the states of a role's grants.
     *
     * @return the states, so the JIT keeps the work
     */
    @Benchmark
    public Map<Long, GrantDerivation.ResourceState> derive()
    {
        return derivation.derive(grants, List.of(), NOW);
    }

    private static ResourceDetails details(String name)
    {
        return new ResourceDetails(name, null, null, true, true, DenyMode.HIDE);
    }
}
