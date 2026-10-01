// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportProblemTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesTheArguments()
    {
        List<Object> arguments = new ArrayList<>(List.of("alice"));
        ImportProblem problem = new ImportProblem(2, "username", IdentityErrorCode.IMPORT_DUPLICATE, arguments);
        arguments.clear();

        assertThat(problem.arguments()).containsExactly("alice");
        assertThatThrownBy(() -> new ImportProblem(2, null, null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ImportProblem(2, null, IdentityErrorCode.IMPORT_EMPTY, null))
                .isInstanceOf(NullPointerException.class);
    }
}
