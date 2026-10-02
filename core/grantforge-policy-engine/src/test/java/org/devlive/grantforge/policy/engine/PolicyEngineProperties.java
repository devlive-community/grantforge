// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laws every engine obeys on random path policies and requests: the prefix index never changes a decision, the
 * order of policies does not matter, more allowances never deny and more denials never allow.
 */
class PolicyEngineProperties
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final List<String> SEGMENTS = List.of("a", "b", "ab", "data", "logs", "x");
    private static final List<String> USERS = List.of("alice", "bob", "carol");
    private static final List<String> ACCESS = List.of("read", "write", "execute");

    /** Random policies and requests on the HDFS-like model. */
    record Case(List<Policy> policies, List<AccessRequest> requests, long seed)
    {
    }

    @Provide
    Arbitrary<Case> cases()
    {
        return Combinators.combine(Arbitraries.integers().between(0, 40), Arbitraries.longs())
                .as((size, seed) -> build(size, new Random(seed), seed));
    }

    private static String path(Random random, boolean pattern)
    {
        StringBuilder path = new StringBuilder();
        int depth = random.nextInt(4);
        for (int level = 0; level < depth; level++) {
            String segment = SEGMENTS.get(random.nextInt(SEGMENTS.size()));
            if (pattern && random.nextInt(6) == 0) {
                segment = random.nextBoolean() ? "*" : segment.charAt(0) + "?";
            }
            path.append('/').append(segment);
        }
        return path.length() == 0 ? "/" : path.toString() + (random.nextInt(5) == 0 ? "/" : "");
    }

    private static PolicyItem item(Random random)
    {
        PolicyItem.Builder item = PolicyItem.builder().accessTypes(ACCESS.get(random.nextInt(ACCESS.size())));
        return random.nextInt(4) == 0 ? item.groups(random.nextBoolean() ? "staff" : PolicyItem.PUBLIC).build()
                : item.users(USERS.get(random.nextInt(USERS.size()))).build();
    }

    private static PolicyItem[] items(Random random, int max)
    {
        int count = random.nextInt(max + 1);
        PolicyItem[] items = new PolicyItem[count];
        for (int index = 0; index < count; index++) {
            items[index] = item(random);
        }
        return items;
    }

    static Policy policy(long id, Random random)
    {
        List<String> values = new ArrayList<>();
        int count = 1 + random.nextInt(2);
        for (int index = 0; index < count; index++) {
            values.add(random.nextInt(8) == 0 ? "*" : path(random, true));
        }
        return Policy.builder(id).priority(random.nextInt(5) == 0 ? Priority.OVERRIDE : Priority.NORMAL)
                .resource("path", ResourceSpec.of(values, random.nextInt(8) == 0, random.nextBoolean()))
                .allow(items(random, 2)).allowExceptions(items(random, 1)).deny(items(random, 1)).denyExceptions(items(random, 1))
                .build();
    }

    private static Case build(int size, Random random, long seed)
    {
        List<Policy> policies = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            policies.add(policy(index + 1, random));
        }
        List<AccessRequest> requests = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            AccessRequest.Builder request = AccessRequest.builder(USERS.get(random.nextInt(USERS.size())),
                    ACCESS.get(random.nextInt(ACCESS.size()))).resource("path", path(random, false)).time(NOW);
            if (random.nextBoolean()) {
                request.groups("staff");
            }
            requests.add(request.build());
        }
        return new Case(policies, requests, seed);
    }

    @Property(tries = 300)
    void thePrefixIndexNeverChangesADecision(@ForAll("cases") Case example)
    {
        PolicyEngine indexed = PolicyEngine.create(Models.HDFS, example.policies(), Map.of());
        PolicyEngine scanning = PolicyEngine.withoutIndex(Models.HDFS, example.policies(), Map.of());
        for (AccessRequest request : example.requests()) {
            assertThat(indexed.evaluate(request)).as(request.resource().toString()).isEqualTo(scanning.evaluate(request));
        }
    }

    @Property(tries = 200)
    void theOrderOfPoliciesDoesNotMatter(@ForAll("cases") Case example)
    {
        List<Policy> shuffled = new ArrayList<>(example.policies());
        Collections.shuffle(shuffled, new Random(example.seed()));
        PolicyEngine given = PolicyEngine.create(Models.HDFS, example.policies(), Map.of());
        PolicyEngine reordered = PolicyEngine.create(Models.HDFS, shuffled, Map.of());
        for (AccessRequest request : example.requests()) {
            assertThat(reordered.evaluate(request)).isEqualTo(given.evaluate(request));
        }
    }

    @Property(tries = 200)
    void moreAllowancesNeverDenyAndMoreDenialsNeverAllow(@ForAll("cases") Case example)
    {
        Random random = new Random(example.seed());
        String value = path(random, true);
        Policy allowing = Policy.builder(1000).resource("path", ResourceSpec.of(List.of(value), false, random.nextBoolean()))
                .allow(item(random), item(random)).build();
        Policy denying = Policy.builder(1001).resource("path", ResourceSpec.of(List.of(value), false, random.nextBoolean()))
                .deny(item(random), item(random)).build();
        List<Policy> withAllowance = new ArrayList<>(example.policies());
        withAllowance.add(allowing);
        List<Policy> withDenial = new ArrayList<>(example.policies());
        withDenial.add(denying);
        PolicyEngine before = PolicyEngine.create(Models.HDFS, example.policies(), Map.of());
        PolicyEngine allowed = PolicyEngine.create(Models.HDFS, withAllowance, Map.of());
        PolicyEngine denied = PolicyEngine.create(Models.HDFS, withDenial, Map.of());
        for (AccessRequest request : example.requests()) {
            Decision was = before.evaluate(request);
            if (was.allowed()) {
                assertThat(allowed.evaluate(request).allowed()).isTrue();
            }
            if (!was.allowed()) {
                assertThat(denied.evaluate(request).allowed()).isFalse();
            }
        }
    }
}
