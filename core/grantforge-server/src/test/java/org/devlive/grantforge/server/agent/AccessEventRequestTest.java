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
        AccessEvent.Fields byPolicy = new AccessEventRequest("e", Instant.EPOCH, "u", " ", "r", "", "read", null, AccessOutcome.ALLOWED, "4",
                1L, null, "x".repeat(1001), null, null).fields();
        assertThat(byPolicy.enforcer()).isEqualTo(Enforcer.GRANTFORGE);
        assertThat(byPolicy.requestText()).hasSize(1000);
        assertThat(byPolicy.clientIp()).isNull();
        assertThat(byPolicy.resourceType()).isNull();
        AccessEvent.Fields bySystem = new AccessEventRequest("e", Instant.EPOCH, "u", null, "r", null, "read", "open", AccessOutcome.DENIED,
                null, null, null, " ", null, null).fields();
        assertThat(bySystem.enforcer()).isEqualTo(Enforcer.NATIVE);
        assertThat(bySystem.requestText()).isNull();
        assertThat(new AccessEventRequest("e", Instant.EPOCH, "u", null, "r", null, "read", null, AccessOutcome.DENIED, null, null,
                Enforcer.GRANTFORGE, "short", null, null).fields()).extracting(AccessEvent.Fields::enforcer, AccessEvent.Fields::requestText)
                .containsExactly(Enforcer.GRANTFORGE, "short");
        assertThat(byPolicy.count()).as("a single access when left out").isEqualTo(1);
        assertThat(byPolicy.lastOccurredAt()).isNull();
    }

    @Test
    void carriesHowManyAccessesAnEventFolds()
    {
        Instant first = Instant.parse("2026-10-17T10:00:00Z");
        AccessEvent.Fields folded = new AccessEventRequest("e", first, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED, null, null,
                null, null, 42, first.plusSeconds(59)).fields();
        assertThat(folded.count()).isEqualTo(42);
        assertThat(folded.lastOccurredAt()).isEqualTo(first.plusSeconds(59));
        AccessEvent.Fields backwards = new AccessEventRequest("e", first, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED, null,
                null, null, null, 2, first.minusSeconds(1)).fields();
        assertThat(backwards.lastOccurredAt()).as("a last moment before the first is dropped").isNull();
        AccessEvent.Fields single = new AccessEventRequest("e", first, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED, null,
                null, null, null, 1, first.plusSeconds(5)).fields();
        assertThat(single.lastOccurredAt()).as("one access has no span").isNull();
    }
}
