// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.MfaEnrollment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaEnrollmentResponseTest
{
    @Test
    void copiesTheEnrollmentAndHidesTheSecret()
    {
        MfaEnrollmentResponse response = MfaEnrollmentResponse.from(new MfaEnrollment("SECRET", "otpauth://totp/x?secret=SECRET"));

        assertThat(response.secret()).isEqualTo("SECRET");
        assertThat(response.uri()).isEqualTo("otpauth://totp/x?secret=SECRET");
        assertThat(response.toString()).doesNotContain("SECRET");
    }
}
