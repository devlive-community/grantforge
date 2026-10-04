// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaEnrollmentTest
{
    @Test
    void keepsTheSecretOutOfItsText()
    {
        MfaEnrollment enrollment = new MfaEnrollment("SECRET", "otpauth://totp/x?secret=SECRET");

        assertThat(enrollment.secret()).isEqualTo("SECRET");
        assertThat(enrollment.uri()).contains("SECRET");
        assertThat(enrollment.toString()).doesNotContain("SECRET");
    }
}
