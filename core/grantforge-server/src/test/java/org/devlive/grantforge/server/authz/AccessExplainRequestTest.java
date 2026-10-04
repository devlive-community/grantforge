// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessExplainRequestTest
{
    @Test
    void carriesTheQuestion()
    {
        assertThat(new AccessExplainRequest(null, AccessKind.RESOURCE, "system.user").check())
                .isEqualTo(new AccessCheckRequest(AccessKind.RESOURCE, "system.user"));
    }
}
