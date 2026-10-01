// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatcherTypeTest
{
    @Test
    void namesArePartOfThePluginApi()
    {
        // Stored in the database and sent to the console; renaming one is a breaking API change.
        assertThat(MatcherType.values()).extracting(Enum::name).containsExactly("EXACT", "WILDCARD", "PATH", "REGEX");
    }
}
