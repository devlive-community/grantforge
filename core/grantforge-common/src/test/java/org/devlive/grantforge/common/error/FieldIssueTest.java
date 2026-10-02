// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldIssueTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsArguments()
    {
        List<Object> arguments = new ArrayList<>(List.of("x"));
        FieldIssue issue = new FieldIssue("url", "error.invalid", arguments);
        arguments.clear();
        assertThat(issue.arguments()).containsExactly("x");
        assertThat(FieldIssue.of("url", "error.required").arguments()).isEmpty();
        assertThatThrownBy(() -> new FieldIssue(null, "k", List.of())).isInstanceOf(NullPointerException.class);
    }
}
