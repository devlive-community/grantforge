// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GrantForgeProblemsTest
{
    @Test
    void answersWithTheReasonsStatusButNotItsDetails()
    {
        ProblemDetail problem = new GrantForgeProblems().refused(new GrantForgeException(Reason.UNAVAILABLE, "connection refused", null));

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getTitle()).isEqualTo("Service Unavailable");
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getProperties()).containsEntry("reason", "UNAVAILABLE");
    }
}
