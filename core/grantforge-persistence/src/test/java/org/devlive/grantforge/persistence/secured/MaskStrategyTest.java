// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MaskStrategyTest
{
    @Test
    void hidesMostOfAValue()
    {
        assertThat(MaskStrategy.EMAIL.mask("alice@acme.io")).isEqualTo("a***@acme.io");
        assertThat(MaskStrategy.EMAIL.mask("@acme.io")).isEqualTo("@***o");
        assertThat(MaskStrategy.EMAIL.mask("no-at-sign")).isEqualTo("n***n");
        assertThat(MaskStrategy.PHONE.mask("13812345678")).isEqualTo("138***5678");
        assertThat(MaskStrategy.PHONE.mask("1234567")).isEqualTo("***");
        assertThat(MaskStrategy.ID_NUMBER.mask("110101199003071234")).isEqualTo("110101***1234");
        assertThat(MaskStrategy.PARTIAL.mask("爱丽丝梦游")).isEqualTo("爱***游");
        assertThat(MaskStrategy.PARTIAL.mask("ab")).isEqualTo("***");
        assertThat(MaskStrategy.PARTIAL.mask("a😀😀b")).isEqualTo("a***b");
        assertThat(MaskStrategy.FULL.mask("secret")).isEqualTo("***");
        assertThat(MaskStrategy.FULL.mask("")).isEmpty();
    }

    @Test
    void readsNamesInAnyCase()
    {
        assertThat(MaskStrategy.of("email")).isEqualTo(MaskStrategy.EMAIL);
        assertThat(MaskStrategy.of("Id_Number")).isEqualTo(MaskStrategy.ID_NUMBER);
        assertThatIllegalArgumentException().isThrownBy(() -> MaskStrategy.of("regex"));
    }
}
