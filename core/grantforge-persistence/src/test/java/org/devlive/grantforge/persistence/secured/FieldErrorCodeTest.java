// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldErrorCodeTest
{
    @Test
    void refusesChangesOfReadOnlyFieldsAsForbidden()
    {
        assertThat(FieldErrorCode.READONLY_CHANGED.code()).isEqualTo("GF-FIELD-001");
        assertThat(FieldErrorCode.READONLY_CHANGED.httpStatus()).isEqualTo(403);
        assertThat(FieldErrorCode.READONLY_CHANGED.messageKey()).isEqualTo("error.field.readonly-changed");
    }
}
