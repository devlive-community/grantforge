// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyViewTest
{
    @Test
    void isActiveUntilRetired()
    {
        assertThat(new SigningKeyView("k", "RS256", Instant.EPOCH, null, null).active()).isTrue();
        assertThat(new SigningKeyView("k", "RS256", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH).active()).isFalse();
    }
}
