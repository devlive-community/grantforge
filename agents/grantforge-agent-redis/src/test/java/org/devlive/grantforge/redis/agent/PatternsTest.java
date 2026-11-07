// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternsTest
{
    @Test
    void grantsOnlyPlainNamesAndWildcards()
    {
        assertThat(Patterns.grantable("sales:*")).isTrue();
        assertThat(Patterns.grantable("a?b")).isTrue();
        for (String wrong : List.of("", "a b", "a[bc]", "a\\b", "(a)")) {
            assertThat(Patterns.grantable(wrong)).as(wrong).isFalse();
        }
    }

    @Test
    void aPatternIsUniformWhenEveryPolicyOverlappingItCoversItWhole()
    {
        Snapshot snapshot = new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*").deny(List.of("bob"), List.of("read"), "key", "sales:secret:*")
                .allow(List.of("carol"), List.of("read"), "key", "*").snapshot();
        Patterns patterns = new Patterns(snapshot, "key");
        assertThat(patterns.values()).containsExactly("*", "sales:*", "sales:secret:*");
        assertThat(patterns.candidates()).containsExactly("sales:secret:*");
        assertThat(patterns.uniform("sales:secret:*")).isTrue();
        assertThat(patterns.uniform("sales:*")).isFalse();
    }

    @Test
    void aPatternIsUniformForAUserItsPoliciesDecideAlikeAcross()
    {
        Snapshot snapshot = new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*").deny(List.of("bob"), List.of("read"), "key", "sales:secret:*")
                .snapshot();
        Patterns patterns = new Patterns(snapshot, "key");
        assertThat(patterns.uniform("sales:*", name -> name.startsWith("sales:"))).as("bob's policy decides alice alike").isTrue();
        assertThat(patterns.uniform("sales:*", name -> !name.startsWith("sales:secret:"))).isFalse();
    }

    @Test
    void cutsAPatternIntoAtMostSoManyRegions()
    {
        RedisSnapshots policies = new RedisSnapshots();
        for (int index = 0; index < 10; index++) {
            policies.allow(List.of("u" + index), List.of("read"), "key", "*" + index + "*");
        }
        Patterns patterns = new Patterns(policies.snapshot(), "key");
        assertThat(patterns.uniform("*", name -> true)).as("over " + Patterns.MAX_REGIONS + " regions").isFalse();
    }

    @Test
    void excludedValuesCoverEverythingElse()
    {
        Snapshot snapshot = new RedisSnapshots().snapshot();
        assertThat(new Patterns(snapshot, "key").values()).isEmpty();
        assertThat(Patterns.region(ResourceSpec.of(List.of("a*"), true, false)).run("b")).isTrue();
    }
}
