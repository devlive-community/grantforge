// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AccessEventView;
import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.devlive.grantforge.service.domain.Enforcer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessEventResponseTest
{
    @Test
    void sendsIdsAsText()
    {
        AccessEvent.Fields fields = new AccessEvent.Fields("e", Instant.EPOCH, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED,
                9_007_199_254_740_993L, 2L, Enforcer.GRANTFORGE, null);
        AccessEventResponse response = AccessEventResponse.from(new AccessEventView(9_007_199_254_740_995L, "a", fields, "p"));
        assertThat(response.id()).isEqualTo("9007199254740995");
        assertThat(response.policyId()).isEqualTo("9007199254740993");
        AccessEvent.Fields none = new AccessEvent.Fields("e", Instant.EPOCH, "u", null, "r", null, "read", null, AccessOutcome.DENIED,
                null, null, Enforcer.NATIVE, null);
        assertThat(AccessEventResponse.from(new AccessEventView(1, "a", none, null)).policyId()).isNull();
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.lastOccurredAt()).isNull();
        AccessEvent.Fields folded = new AccessEvent.Fields("e", Instant.EPOCH, "u", null, "r", null, "read", null, AccessOutcome.ALLOWED,
                null, null, Enforcer.GRANTFORGE, null, 7, Instant.EPOCH.plusSeconds(30));
        AccessEventResponse many = AccessEventResponse.from(new AccessEventView(2, "a", folded, null));
        assertThat(many.count()).isEqualTo(7);
        assertThat(many.lastOccurredAt()).isEqualTo(Instant.EPOCH.plusSeconds(30));
    }
}
