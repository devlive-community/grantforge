// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessKind;
import org.devlive.grantforge.authz.application.AccessResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessResultsResponseTest
{
    @Test
    void namesTheAccountAsTextAndCopiesTheAnswers()
    {
        AccessResultsResponse response = AccessResultsResponse.from(99_556_122_317_295_616L,
                List.of(new AccessResult(AccessKind.PERMISSION, "system.user.read", true)));
        assertThat(response.accountId()).isEqualTo("99556122317295616");
        assertThat(response.results()).containsExactly(new AccessResultsResponse.Result(AccessKind.PERMISSION, "system.user.read", true));
    }
}
