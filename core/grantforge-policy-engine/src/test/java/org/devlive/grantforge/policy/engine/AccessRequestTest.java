// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessRequestTest
{
    @Test
    void describesWhoWantsWhatHowAndWhen()
    {
        Instant moment = Instant.parse("2026-06-15T12:00:00Z");
        AccessRequest request = AccessRequest.builder("alice", "read").resource("path", "/a").groups("g").roles("r").time(moment)
                .context(Map.of("clientAddress", "10.0.0.1")).build();
        assertThat(request.user()).isEqualTo("alice");
        assertThat(request.accessType()).isEqualTo("read");
        assertThat(request.resource()).containsEntry("path", "/a");
        assertThat(request.groups()).containsExactly("g");
        assertThat(request.roles()).containsExactly("r");
        assertThat(request.time()).isEqualTo(moment);
        assertThat(request.context()).containsEntry("clientAddress", "10.0.0.1");
        assertThat(AccessRequest.builder("a", "read").resource("path", "/").build().time()).isNotNull();
        assertThatThrownBy(() -> AccessRequest.builder("a", "read").build()).hasMessageContaining("names a resource");
    }
}
