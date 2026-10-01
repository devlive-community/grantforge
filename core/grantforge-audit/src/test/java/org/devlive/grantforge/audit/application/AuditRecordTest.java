// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditRecordTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresActionAndOutcome()
    {
        assertThat(new AuditRecord(AuditAction.LOGOUT, AuditOutcome.SUCCESS, null, null, null, null, null).action())
                .isEqualTo(AuditAction.LOGOUT);
        assertThatThrownBy(() -> new AuditRecord(null, AuditOutcome.SUCCESS, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditRecord(AuditAction.LOGOUT, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
