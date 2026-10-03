// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessCheck;
import org.devlive.grantforge.authz.application.AccessKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessCheckRequestTest
{
    @Test
    void becomesAQuestion()
    {
        assertThat(new AccessCheckRequest(AccessKind.PERMISSION, " system.user.read ").toCheck())
                .isEqualTo(new AccessCheck(AccessKind.PERMISSION, "system.user.read"));
    }
}
