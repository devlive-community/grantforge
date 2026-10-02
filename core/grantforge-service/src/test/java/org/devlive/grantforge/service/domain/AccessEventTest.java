// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessEventTest
{
    @Test
    void keepsWhatHappened()
    {
        AccessEvent.Fields fields = new AccessEvent.Fields("e1", Instant.EPOCH, "alice", "10.0.0.1", "sales/orders", "table", "select",
                "SELECT", AccessOutcome.DENIED, null, 7L, Enforcer.NATIVE, "select 1");
        AccessEvent event = AccessEvent.of(3, "hs2-1", fields);
        assertThat(event.fields()).isEqualTo(fields);
        assertThat(event).extracting(AccessEvent::getServiceId, AccessEvent::getAgentInstance, AccessEvent::getEventId,
                AccessEvent::getOccurredAt).containsExactly(3L, "hs2-1", "e1", Instant.EPOCH);
    }
}
