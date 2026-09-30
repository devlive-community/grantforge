// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CommonErrorCodeTest
{
    @ParameterizedTest
    @EnumSource(CommonErrorCode.class)
    void everyCodeIsWellFormed(CommonErrorCode error)
    {
        assertThat(error.code()).matches("GF-COMMON-\\d{3}").endsWith(String.valueOf(error.httpStatus()));
        assertThat(error.httpStatus()).isBetween(400, 599);
        assertThat(error.messageKey()).matches("error\\.common\\.[a-z-]+");
    }

    @Test
    void codesAndKeysAreUnique()
    {
        assertThat(Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::code)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::messageKey)).doesNotHaveDuplicates();
    }

    @Test
    void forStatusMapsKnownAndUnknownStatuses()
    {
        assertThat(CommonErrorCode.forStatus(404)).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(CommonErrorCode.forStatus(418)).isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(CommonErrorCode.forStatus(503)).isEqualTo(CommonErrorCode.INTERNAL);
        assertThat(CommonErrorCode.forStatus(200)).isEqualTo(CommonErrorCode.INTERNAL);
    }
}
