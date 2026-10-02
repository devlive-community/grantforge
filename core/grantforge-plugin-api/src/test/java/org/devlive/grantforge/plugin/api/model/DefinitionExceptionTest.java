// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionExceptionTest
{
    @Test
    void keepsEveryProblem()
    {
        DefinitionException invalid = new DefinitionException("demo", List.of("one", "two"));

        assertThat(invalid).hasMessage("invalid service type demo: one; two");
        assertThat(invalid.getProblems()).containsExactly("one", "two");
    }
}
