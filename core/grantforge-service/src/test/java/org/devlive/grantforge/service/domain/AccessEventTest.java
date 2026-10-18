// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(fields.count()).isEqualTo(1);
    }

    @Test
    void keepsHowManyAccessesAnEventFolds()
    {
        Instant last = Instant.EPOCH.plusSeconds(60);
        AccessEvent.Fields folded = new AccessEvent.Fields("e2", Instant.EPOCH, "alice", null, "orders", "topic", "write", "PRODUCE",
                AccessOutcome.ALLOWED, 4L, 7L, Enforcer.GRANTFORGE, null, 1200, last);
        assertThat(AccessEvent.of(3, "broker-1", folded).fields()).isEqualTo(folded);
        assertThat(folded.lastOccurredAt()).isEqualTo(last);
        assertThatThrownBy(() -> new AccessEvent.Fields("e3", Instant.EPOCH, "alice", null, "orders", "topic", "write", null,
                AccessOutcome.ALLOWED, null, null, Enforcer.GRANTFORGE, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
