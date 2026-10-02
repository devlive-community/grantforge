// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointChangeTest
{
    @Test
    void namesFitTheChangeColumn()
    {
        // Stored by name in the VARCHAR(16) change_kind column.
        assertThat(EndpointChange.values()).extracting(Enum::name).containsExactly("ADDED", "CHANGED", "REMOVED");
    }
}
