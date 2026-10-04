// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthSubjectTest
{
    @Test
    void needsPositiveIdentifiers()
    {
        assertThat(new OAuthSubject(1, 2, "ada", Instant.EPOCH).username()).isEqualTo("ada");
        assertThatThrownBy(() -> new OAuthSubject(0, 2, "ada", Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthSubject(1, 0, "ada", Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }
}
