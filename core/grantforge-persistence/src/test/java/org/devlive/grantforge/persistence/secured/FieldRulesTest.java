// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldRulesTest
{
    @Test
    void withoutFieldPoliciesEveryFieldIsVisible()
    {
        assertThat(FieldRules.open().read(7L, "user", "email")).isEqualTo(FieldView.VISIBLE);
    }
}
