// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonOperatorTest
{
    @Test
    void knowsItsNameFieldsAndValues()
    {
        assertThat(ComparisonOperator.of("starts_with")).contains(ComparisonOperator.STARTS_WITH);
        assertThat(ComparisonOperator.of("like")).isEmpty();
        assertThat(ComparisonOperator.LT.appliesTo(DataFieldType.TIME)).isTrue();
        assertThat(ComparisonOperator.LT.appliesTo(DataFieldType.TEXT)).isFalse();
        assertThat(ComparisonOperator.NOT_IN.takesList()).isTrue();
        assertThat(ComparisonOperator.EQ.takesList()).isFalse();
        assertThat(ComparisonOperator.NOT_NULL.takesValue()).isFalse();
        assertThat(ComparisonOperator.CONTAINS.takesValue()).isTrue();
        assertThat(ComparisonOperator.NE.symbol()).isEqualTo("ne");
    }
}
