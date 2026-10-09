// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupExceptionTest
{
    @Test
    void carriesTheReasonTheMessageAndTheCause()
    {
        IOException cause = new IOException("Permission denied: user=grantforge");
        LookupException failure = new LookupException(LookupException.Reason.ACCESS_DENIED, "no access to /data", cause);

        assertThat(failure.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(failure).hasMessage("no access to /data").hasCause(cause);
        assertThat(new LookupException(LookupException.Reason.FAILED, "broken").getCause()).isNull();
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void needsAReasonAndAMessage()
    {
        assertThatThrownBy(() -> new LookupException(null, "x")).isInstanceOf(NullPointerException.class).hasMessage("reason");
        assertThatThrownBy(() -> new LookupException(LookupException.Reason.FAILED, null)).isInstanceOf(NullPointerException.class)
                .hasMessage("message");
    }
}
