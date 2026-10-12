// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RowFilterTest
{
    @Test
    void preservesTheTargetsExpressionAndTheSnapshotThatSelectedIt()
    {
        String expression = " (region = 'EU' OR region IS NULL)\nAND account_id = 42 ";
        RowFilter filter = new RowFilter(8, 31, expression);

        assertThat(filter.expression()).isEqualTo(expression);
        assertThat(filter.policyVersion()).isEqualTo(8);
        assertThat(filter.policyId()).isEqualTo(31);
        assertThat(filter).hasToString(expression + " by policy 31");
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void refusesAnAbsentConditionRatherThanLeavingRowsUnfiltered()
    {
        assertThatNullPointerException().isThrownBy(() -> new RowFilter(8, 31, null))
                .withMessage("expression");
    }
}
