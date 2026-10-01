// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionTest
{
    @Test
    void namesFitTheActionColumn()
    {
        // The action column is VARCHAR(64); stored names must never change.
        assertThat(Arrays.stream(AuditAction.values()).map(Enum::name)).allSatisfy(name -> assertThat(name).hasSizeLessThanOrEqualTo(64))
                .contains("LOGIN_SUCCEEDED", "LOGIN_FAILED", "ACCOUNT_LOCKED", "LOGOUT", "SESSION_REVOKED", "PASSWORD_CHANGED");
    }
}
