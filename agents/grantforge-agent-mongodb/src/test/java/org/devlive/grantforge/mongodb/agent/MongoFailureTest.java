// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoFailureTest
{
    @Test
    void saysWhetherNoServerCouldBeReached()
    {
        IllegalStateException cause = new IllegalStateException();
        MongoFailure failure = new MongoFailure("no server", true, cause);
        assertThat(failure.unreachable()).isTrue();
        assertThat(failure).hasMessage("no server").hasCause(cause);
        assertThat(new MongoFailure("refused", false, null).unreachable()).isFalse();
    }
}
