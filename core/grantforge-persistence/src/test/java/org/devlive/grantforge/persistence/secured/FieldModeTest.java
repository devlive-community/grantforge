// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FieldModeTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void fieldsAreOpenUnlessAPolicySaysOtherwise()
    {
        assertThat(FieldMode.OPEN).isEqualTo(new FieldMode(FieldView.VISIBLE, FieldWriteMode.EDITABLE));
        assertThatNullPointerException().isThrownBy(() -> new FieldMode(FieldView.HIDDEN, null));
    }
}
