// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.AssignmentView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentResponseTest
{
    @Test
    void flattensTheSubjectAndTerms()
    {
        Instant end = Instant.parse("2026-12-31T00:00:00Z");
        AssignmentView view = new AssignmentView(9_007_199_254_740_993L, 2, new Subject(SubjectType.ORG_UNIT, 3, "Sales", "sales"),
                new RoleAssignment.Terms(null, end, true), true);

        assertThat(AssignmentResponse.from(view)).isEqualTo(new AssignmentResponse("9007199254740993", "2", SubjectType.ORG_UNIT, "3",
                "Sales", "sales", null, end, true, true));
    }
}
