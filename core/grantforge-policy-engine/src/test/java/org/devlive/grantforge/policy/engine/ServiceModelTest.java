// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceModelTest
{
    @Test
    void knowsTheChainsAndWhatGrantsEachAccessType()
    {
        assertThat(Models.HIVE.chain("column")).extracting(ResourceLevel::name).containsExactly("database", "table", "column");
        assertThat(Models.HIVE.grantedBy("select")).containsExactlyInAnyOrder("select", "all");
        assertThat(Models.HIVE.grantedBy("all")).containsExactly("all");
        assertThat(Models.HIVE.grantedBy("fly")).isEmpty();
        assertThat(Models.HIVE.hasAccessType("drop")).isTrue();
        assertThat(Models.HIVE.level("schema")).isNull();
        assertThatThrownBy(() -> Models.HIVE.chain("schema")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void impliedAccessTypesChainAndTheModelMustBeConsistent()
    {
        ServiceModel chained = ServiceModel.builder().level(ResourceLevel.of("a", null, MatcherKind.EXACT, true))
                .accessType("read").accessType("write", "read").accessType("admin", "write").build();
        assertThat(chained.grantedBy("read")).containsExactlyInAnyOrder("read", "write", "admin");

        ServiceModel.Builder builder = ServiceModel.builder().level(ResourceLevel.of("a", null, MatcherKind.EXACT, true));
        assertThatThrownBy(() -> builder.level(ResourceLevel.of("a", null, MatcherKind.EXACT, true))).hasMessageContaining("twice");
        assertThatThrownBy(() -> builder.level(ResourceLevel.of("b", "z", MatcherKind.EXACT, true))).hasMessageContaining("unknown parent");
        builder.accessType("read");
        assertThatThrownBy(() -> builder.accessType("read")).hasMessageContaining("twice");
        builder.accessType("write", "fly");
        assertThatThrownBy(builder::build).hasMessageContaining("implies unknown fly");
        assertThatThrownBy(() -> ServiceModel.builder().build()).hasMessageContaining("at least one");
    }
}
