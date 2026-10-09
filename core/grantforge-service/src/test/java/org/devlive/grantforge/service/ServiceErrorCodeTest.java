// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceErrorCodeTest
{
    @Test
    void codesAreUniqueAndStatusesFit()
    {
        assertThat(ServiceErrorCode.values()).extracting(ServiceErrorCode::code).doesNotHaveDuplicates()
                .allMatch(code -> code.startsWith("GF-SERVICE-"));
        assertThat(ServiceErrorCode.PLUGIN_FAILED.httpStatus()).isEqualTo(502);
        assertThat(ServiceErrorCode.CONFIG_INVALID.messageKey()).isEqualTo("error.service.config-invalid");
    }

    @Test
    void aLookupFailureTheOperatorCanActOnIsAClientError()
    {
        // The console names the reason of a problem below 500 only, so these must stay 4xx for it to show them.
        assertThat(List.of(ServiceErrorCode.LOOKUP_UNSUPPORTED, ServiceErrorCode.LOOKUP_NOT_FOUND,
                ServiceErrorCode.LOOKUP_DENIED, ServiceErrorCode.LOOKUP_UNREACHABLE,
                ServiceErrorCode.LOOKUP_AUTHENTICATION_FAILED, ServiceErrorCode.LOOKUP_LIMIT_EXCEEDED,
                ServiceErrorCode.LOOKUP_INVALID_INPUT)).allMatch(code -> code.httpStatus() < 500);
        assertThat(ServiceErrorCode.LOOKUP_UNREACHABLE.httpStatus()).isEqualTo(424);
    }
}
