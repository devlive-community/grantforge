// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.devlive.grantforge.service.domain.Enforcer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessEventViewTest
{
    @Test
    void namesTheDecidingPolicy()
    {
        AccessEvent.Fields fields = new AccessEvent.Fields("e", Instant.EPOCH, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED, 4L,
                null, Enforcer.GRANTFORGE, null);
        assertThat(new AccessEventView(1, "a", fields, "p").policyName()).isEqualTo("p");
    }
}
