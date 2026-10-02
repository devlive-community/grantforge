// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatcherKindTest
{
    @Test
    void offersTheMatchersOfThePluginApi()
    {
        assertThat(MatcherKind.values()).extracting(Enum::name).containsExactly("EXACT", "WILDCARD", "PATH", "REGEX");
    }
}
