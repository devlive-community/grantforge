// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFailureTest
{
    @Test
    void saysWhetherTheNodeCouldNotBeReached()
    {
        IllegalStateException cause = new IllegalStateException();
        RedisFailure failure = new RedisFailure("down", true, cause);
        assertThat(failure.unreachable()).isTrue();
        assertThat(failure).hasMessage("down").hasCause(cause);
        assertThat(new RedisFailure("refused", false, null).unreachable()).isFalse();
    }
}
