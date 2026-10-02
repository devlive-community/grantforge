// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginCallExceptionTest
{
    @Test
    void keepsTheMessageAndCause()
    {
        IllegalStateException cause = new IllegalStateException("x");
        PluginCallException failure = new PluginCallException("hdfs failed", cause);
        assertThat(failure).hasMessage("hdfs failed").hasCause(cause);
    }
}
