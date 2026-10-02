// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrefixIndexTest
{
    private static CompiledPolicy path(long id, String value, boolean excludes)
    {
        return new CompiledPolicy(Policy.builder(id).resource("path", ResourceSpec.of(List.of(value), excludes, false)).build(),
                Models.HDFS);
    }

    @Test
    void offersOnlyPoliciesWhoseValuesCouldMatch()
    {
        PrefixIndex index = new PrefixIndex(List.of(path(1, "/data/sales", false), path(2, "/data/*", false), path(3, "/logs", false),
                path(4, "*", false), path(5, "/x", true), path(6, "?a", false)));

        assertThat(index.candidates("path", "/data/sales/2026").stream().toArray()).containsExactly(0, 1, 3, 4, 5);
        assertThat(index.candidates("path", "/logs").stream().toArray()).containsExactly(2, 3, 4, 5);
        assertThat(index.candidates("path", "/other").stream().toArray()).containsExactly(3, 4, 5);
        assertThat(index.candidates("other", "/data").isEmpty()).isTrue();
    }
}
