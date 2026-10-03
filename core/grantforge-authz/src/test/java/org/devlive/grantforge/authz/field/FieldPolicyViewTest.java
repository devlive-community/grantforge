// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import org.devlive.grantforge.authz.domain.FieldPolicy;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPolicyViewTest
{
    @Test
    void copiesAPolicy()
    {
        FieldPolicy policy = FieldPolicy.create(7, "user", "email", FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.READONLY);
        assertThat(FieldPolicyView.from(policy)).isEqualTo(new FieldPolicyView("user", "email", FieldReadMode.MASKED, MaskStrategy.EMAIL,
                FieldWriteMode.READONLY));
    }
}
