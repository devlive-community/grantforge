// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessExplanation;
import org.devlive.grantforge.authz.application.AccessKind;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessExplanationResponseTest
{
    @Test
    void convertsPathsHoldersAndDenials()
    {
        AccessExplanation explanation = new AccessExplanation(AccessKind.RESOURCE, "system.user", AccessExplanation.Outcome.DENIED, "Users",
                List.of(new AccessExplanation.Path(List.of(new AccessExplanation.PathRole("editors", "Editors",
                        List.of(new Subject(SubjectType.GROUP, 9_007_199_254_740_993L, "Developers", "dev")))),
                        List.of(new AccessExplanation.PathResource("system.user", "Users", ResourceType.PAGE, AccessExplanation.Via.GRANT)))),
                List.of(new AccessExplanation.Denial("nobody", "Nobody", "system")));

        AccessExplanationResponse response = AccessExplanationResponse.from(7, explanation);

        assertThat(response.accountId()).isEqualTo("7");
        assertThat(response.outcome()).isEqualTo(AccessExplanation.Outcome.DENIED);
        assertThat(response.paths().get(0).roles().get(0).assignedTo()).containsExactly(
                new AccessExplanationResponse.Holder(SubjectType.GROUP, "9007199254740993", "Developers", "dev"));
        assertThat(response.paths().get(0).resources()).containsExactly(new AccessExplanationResponse.PathResource("system.user", "Users",
                ResourceType.PAGE, AccessExplanation.Via.GRANT));
        assertThat(response.denials()).containsExactly(new AccessExplanationResponse.Denial("nobody", "Nobody", "system"));
    }
}
