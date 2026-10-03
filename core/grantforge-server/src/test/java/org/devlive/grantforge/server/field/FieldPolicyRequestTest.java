// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import org.devlive.grantforge.authz.field.FieldPolicyCommand;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPolicyRequestTest
{
    @Test
    void becomesACommandEditableUnlessSaidOtherwise()
    {
        assertThat(new FieldPolicyRequest(" user ", "email ", FieldReadMode.MASKED, MaskStrategy.EMAIL, null).toCommand())
                .isEqualTo(new FieldPolicyCommand("user", "email", FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.EDITABLE));
        assertThat(new FieldPolicyRequest("user", "email", FieldReadMode.VISIBLE, null, FieldWriteMode.READONLY).toCommand().writeMode())
                .isEqualTo(FieldWriteMode.READONLY);
    }
}
