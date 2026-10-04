// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthPropertiesTest
{
    @Test
    void checksTheDurationsAndTrimsTheIssuer()
    {
        assertThat(new OAuthProperties(" https://id.example ", Duration.ofDays(90), Duration.ofDays(2)).issuer()).isEqualTo("https://id.example");
        assertThat(new OAuthProperties(" ", Duration.ZERO, Duration.ofDays(2)).issuer()).isNull();
        assertThatThrownBy(() -> new OAuthProperties(null, Duration.ofDays(-1), Duration.ofDays(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthProperties(null, Duration.ofDays(1), Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("signing-key-retention");
    }
}
