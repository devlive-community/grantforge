// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FieldViewTest
{
    @Test
    void showsValuesAsTheReaderMaySeeThem()
    {
        Instant at = Instant.parse("2026-10-02T08:00:00Z");
        assertThat(FieldView.VISIBLE.present("alice@acme.io")).isEqualTo("alice@acme.io");
        assertThat(FieldView.VISIBLE.present(at)).isEqualTo(at);
        assertThat(FieldView.HIDDEN.present("alice@acme.io")).isNull();
        FieldView masked = FieldView.masked(MaskStrategy.EMAIL);
        assertThat(masked.present("alice@acme.io")).isEqualTo("a***@acme.io");
        assertThat(masked.present(new StringBuilder("bob@acme.io"))).isEqualTo("b***@acme.io");
        // Only text can be masked; anything else shows nothing.
        assertThat(masked.present(at)).isNull();
        assertThat(masked.present(null)).isNull();
    }

    @Test
    void takesAMaskOnlyWhenMasked()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> new FieldView(FieldReadMode.MASKED, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new FieldView(FieldReadMode.HIDDEN, MaskStrategy.FULL));
        assertThat(new FieldView(FieldReadMode.HIDDEN, null)).isEqualTo(FieldView.HIDDEN);
    }
}
