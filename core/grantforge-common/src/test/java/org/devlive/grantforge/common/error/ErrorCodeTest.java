// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract every {@link ErrorCode} implementation must satisfy; feature modules add theirs to the list. */
class ErrorCodeTest
{
    private static List<ErrorCode> allErrorCodes()
    {
        return Stream.<ErrorCode>of(CommonErrorCode.values()).toList();
    }

    @Test
    void everyErrorCodeHonoursTheContract()
    {
        assertThat(allErrorCodes()).isNotEmpty().allSatisfy(error -> {
            assertThat(error.code()).as("code").matches("GF-[A-Z]+-\\d{3}");
            assertThat(error.httpStatus()).as("status of %s", error.code()).isBetween(400, 599);
            assertThat(error.messageKey()).as("key of %s", error.code()).matches("error(\\.[a-z][a-z-]*)+");
        });
    }

    @Test
    void codesAreGloballyUnique()
    {
        assertThat(allErrorCodes().stream().map(ErrorCode::code)).doesNotHaveDuplicates();
    }
}
