// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.devlive.grantforge.service.domain.Enforcer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessEventRequestTest
{
    @Test
    void namesWhoDecidedAndCutsLongRequests()
    {
        AccessEvent.Fields byPolicy = new AccessEventRequest("e", Instant.EPOCH, "u", " ", "r", "", "read", null, AccessOutcome.ALLOWED, 4L,
                1L, null, "x".repeat(1001)).fields();
        assertThat(byPolicy.enforcer()).isEqualTo(Enforcer.GRANTFORGE);
        assertThat(byPolicy.requestText()).hasSize(1000);
        assertThat(byPolicy.clientIp()).isNull();
        assertThat(byPolicy.resourceType()).isNull();
        AccessEvent.Fields bySystem = new AccessEventRequest("e", Instant.EPOCH, "u", null, "r", null, "read", "open", AccessOutcome.DENIED,
                null, null, null, " ").fields();
        assertThat(bySystem.enforcer()).isEqualTo(Enforcer.NATIVE);
        assertThat(bySystem.requestText()).isNull();
        assertThat(new AccessEventRequest("e", Instant.EPOCH, "u", null, "r", null, "read", null, AccessOutcome.DENIED, null, null,
                Enforcer.GRANTFORGE, "short").fields()).extracting(AccessEvent.Fields::enforcer, AccessEvent.Fields::requestText)
                .containsExactly(Enforcer.GRANTFORGE, "short");
    }
}
