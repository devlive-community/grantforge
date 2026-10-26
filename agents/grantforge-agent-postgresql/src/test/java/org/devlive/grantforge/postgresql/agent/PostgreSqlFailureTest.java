// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlFailureTest
{
    @Test
    void tellsAServerThatCannotBeReachedFromOneThatRefuses()
    {
        assertThat(new PostgreSqlFailure("x", "08006", null).unreachable()).isTrue();
        assertThat(new PostgreSqlFailure("x", "57P01", null).unreachable()).isTrue();
        assertThat(new PostgreSqlFailure("x", "53300", null).unreachable()).isTrue();
        assertThat(new PostgreSqlFailure("x", null, null).unreachable()).as("no SQLSTATE: the driver could not talk to a server").isTrue();
        PostgreSqlFailure refused = new PostgreSqlFailure("permission denied", PostgreSqlFailure.INSUFFICIENT_PRIVILEGE, new IllegalStateException());
        assertThat(refused.unreachable()).isFalse();
        assertThat(refused.state()).isEqualTo("42501");
        assertThat(refused).hasMessage("permission denied").hasCauseInstanceOf(IllegalStateException.class);
    }
}
