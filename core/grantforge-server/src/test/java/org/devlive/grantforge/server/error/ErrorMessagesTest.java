// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.error;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMessagesTest
{
    @Test
    void fallsBackToTheCodeWithoutATranslation()
    {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage(CommonErrorCode.NOT_FOUND.messageKey(), Locale.getDefault(), "missing {0}");
        ErrorMessages messages = new ErrorMessages(source);

        assertThat(messages.text(CommonErrorCode.NOT_FOUND, List.of("x"))).isEqualTo("missing x");
        assertThat(messages.text(CommonErrorCode.CONFLICT, List.of())).isEqualTo("GF-COMMON-409");
    }
}
