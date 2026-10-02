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
    }
}
