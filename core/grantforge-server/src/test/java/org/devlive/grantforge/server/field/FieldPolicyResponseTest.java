// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import org.devlive.grantforge.authz.field.FieldPolicyView;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPolicyResponseTest
{
    @Test
    void copiesAPolicy()
    {
        assertThat(FieldPolicyResponse.from(new FieldPolicyView("user", "email", FieldReadMode.MASKED, MaskStrategy.PHONE,
                FieldWriteMode.EDITABLE))).isEqualTo(new FieldPolicyResponse("user", "email", FieldReadMode.MASKED, MaskStrategy.PHONE,
                FieldWriteMode.EDITABLE));
    }
}
