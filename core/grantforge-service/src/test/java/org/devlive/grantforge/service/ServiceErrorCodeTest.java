// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.junit.jupiter.api.Test;

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
}
