// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FieldPolicyCommandTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void needsTheFieldAndItsModes()
    {
        assertThat(new FieldPolicyCommand("user", "email", FieldReadMode.HIDDEN, null, FieldWriteMode.EDITABLE).maskStrategy()).isNull();
        assertThatNullPointerException().isThrownBy(() -> new FieldPolicyCommand("user", "email", null, null, FieldWriteMode.EDITABLE));
    }
}
