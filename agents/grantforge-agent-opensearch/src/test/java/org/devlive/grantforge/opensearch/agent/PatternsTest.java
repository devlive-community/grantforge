// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternsTest
{
    private static Patterns patterns(OpenSearchSnapshots snapshots)
    {
        return new Patterns(snapshots.snapshot(), Planner.INDEX);
    }

    @Test
    void wildcardsMatchAsThePolicyEngineDoes()
    {
        assertThat(Patterns.glob("logs-*").run("logs-")).isTrue();
        assertThat(Patterns.glob("logs-*").run("logs-eu")).isTrue();
        assertThat(Patterns.glob("logs-*").run("log")).isFalse();
        assertThat(Patterns.glob("a?c").run("abc")).isTrue();
        assertThat(Patterns.glob("a?c").run("ac")).isFalse();
        assertThat(Patterns.glob("*").run("")).isTrue();
        assertThat(Patterns.glob("").run("")).isTrue();
        assertThat(Patterns.glob("logs-*").run("logs-*")).as("a pattern matches itself").isTrue();
        assertThat(Patterns.overlap("a*", "*b")).isTrue();
        assertThat(Patterns.overlap("a*", "b*")).isFalse();
    }

    @Test
    void aPatternHoldsTheSameWhereEveryPolicyThatOverlapsItCoversItWhole()
    {
        Patterns patterns = patterns(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "logs-*", "field", "*")
                .deny(List.of("bob"), List.of("index"), "index", "logs-secret").allow(List.of("bob"), List.of("index"), "index", "*")
                .allow(List.of("carol"), List.of("read"), "index", "metrics-*", "field", "*"));
        assertThat(patterns.uniform("logs-*")).as("logs-secret is decided otherwise").isFalse();
        assertThat(patterns.uniform("logs-eu")).isTrue();
        assertThat(patterns.uniform("metrics-*")).isTrue();
        assertThat(patterns.uniform("metrics-cpu-*")).as("within a pattern that holds the same").isTrue();
        assertThat(patterns.uniform("*")).isFalse();
        assertThat(patterns.candidates()).containsExactly("logs-secret", "metrics-*");
    }

    @Test
    void masksAndRowFiltersCountAndExcludedValuesCoverTheRest()
    {
        Patterns masked = patterns(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "logs-*", "field", "*")
                .mask(List.of("alice"), "mask_hash", null, "logs-eu", "card"));
        assertThat(masked.uniform("logs-*")).isFalse();
        Patterns filtered = patterns(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "logs-*", "field", "*")
                .filter(List.of("alice"), "{\"match_all\": {}}", "logs-*"));
        assertThat(filtered.uniform("logs-*")).isTrue();
        Patterns excluding = patterns(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "!hr", "field", "*")
                .allow(List.of("bob"), List.of("read"), "index", "logs-*", "field", "*"));
        assertThat(excluding.uniform("logs-*")).as("everything but hr covers logs-* whole").isTrue();
        assertThat(excluding.uniform("h*")).isFalse();
        assertThat(excluding.candidates()).as("excluded values are no candidates").containsExactly("logs-*");
    }

    @Test
    void grantsOnlyWhatOpenSearchReadsAsANameOrAWildcard()
    {
        assertThat(Patterns.grantable("logs-*")).isTrue();
        assertThat(Patterns.grantable("a?b")).isTrue();
        for (String value : new String[] {"", "-logs", "_all", "+x", "a,b", "a/b", "/re/", "${user.name}", "a b", "a:b", "a#b", "a|b"}) {
            assertThat(Patterns.grantable(value)).as(value).isFalse();
        }
        Patterns patterns = patterns(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "team-${user.name}", "field", "*"));
        assertThat(patterns.candidates()).isEmpty();
    }

    @Test
    void findsTheCandidateThatCoversANameAndWhatAPolicyCovers()
    {
        Patterns patterns = patterns(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "logs-*", "field", "*"));
        assertThat(patterns.covering("logs-eu")).isEqualTo("logs-*");
        assertThat(patterns.covering("hr")).isNull();
        assertThat(patterns.matches("logs-*", "logs-us")).isTrue();
        assertThat(patterns.covers(ResourceSpec.of(List.of("hr"), true, false), "logs")).isTrue();
        assertThat(patterns.covers(ResourceSpec.of(List.of("hr"), true, false), "hr")).isFalse();
        assertThat(patterns.covers(ResourceSpec.of("a*", "b*"), "b1")).isTrue();
        assertThat(patterns.covers(ResourceSpec.of("a*"), "c")).isFalse();
    }
}
