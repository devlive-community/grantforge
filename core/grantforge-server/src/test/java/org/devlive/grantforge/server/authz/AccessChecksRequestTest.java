// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessChecksRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the list gives null
    void copiesTheQuestionsAndTakesNoneForMissing()
    {
        List<AccessCheckRequest> given = new ArrayList<>(List.of(new AccessCheckRequest(AccessKind.RESOURCE, "system")));
        AccessChecksRequest request = new AccessChecksRequest("7", given);
        given.clear();
        assertThat(request.checks()).hasSize(1);
        assertThat(new AccessChecksRequest(null, null).checks()).isEmpty();
    }
}
