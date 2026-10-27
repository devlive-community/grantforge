// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DorisFailureTest
{
    @Test
    void saysWhetherNoFrontendCouldBeReached()
    {
        IllegalStateException cause = new IllegalStateException("down");
        DorisFailure unreachable = new DorisFailure("no frontend", true, cause);
        assertThat(unreachable.unreachable()).isTrue();
        assertThat(unreachable).hasMessage("no frontend").hasCause(cause);
        assertThat(new DorisFailure("refused", false, null).unreachable()).isFalse();
    }
}
