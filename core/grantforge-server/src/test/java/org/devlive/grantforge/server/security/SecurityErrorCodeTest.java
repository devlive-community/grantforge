// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorCodeTest
{
    @Test
    void csrfRejectionIsAForbiddenSecurityError()
    {
        assertThat(SecurityErrorCode.CSRF_REJECTED.code()).isEqualTo("GF-SECURITY-001");
        assertThat(SecurityErrorCode.CSRF_REJECTED.httpStatus()).isEqualTo(403);
        assertThat(SecurityErrorCode.CSRF_REJECTED.messageKey()).isEqualTo("error.security.csrf-rejected");
    }
}
