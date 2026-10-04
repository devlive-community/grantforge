// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FieldPolicyTest
{
    @Test
    void describesHowARoleSeesAField()
    {
        FieldPolicy policy = FieldPolicy.create(7, "user", "email", FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.READONLY);
        assertThat(policy).extracting(FieldPolicy::getRoleId, FieldPolicy::getEntityCode, FieldPolicy::getFieldCode,
                FieldPolicy::getReadMode, FieldPolicy::getMaskStrategy, FieldPolicy::getWriteMode)
                .containsExactly(7L, "user", "email", FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.READONLY);
        assertThat(policy.copyTo(8)).extracting(FieldPolicy::getRoleId, FieldPolicy::getMaskStrategy).containsExactly(8L, MaskStrategy.EMAIL);
    }

    @Test
    void takesAMaskOnlyWhenMasked()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldPolicy.create(7, "user", "email", FieldReadMode.MASKED, null,
                FieldWriteMode.EDITABLE));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldPolicy.create(7, "user", "email", FieldReadMode.HIDDEN,
                MaskStrategy.FULL, FieldWriteMode.EDITABLE));
    }
}
