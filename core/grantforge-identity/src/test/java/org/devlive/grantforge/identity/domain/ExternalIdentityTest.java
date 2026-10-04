// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalIdentityTest
{
    @Test
    void linksAnAccountToWhatTheSourceCallsTheUser()
    {
        ExternalIdentity link = ExternalIdentity.of(7, 3, "uuid-1");

        assertThat(link.getAccountId()).isEqualTo(7);
        assertThat(link.getSourceId()).isEqualTo(3);
        assertThat(link.getExternalId()).isEqualTo("uuid-1");
        assertThatThrownBy(() -> ExternalIdentity.of(7, 3, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
