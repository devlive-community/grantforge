// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DataMaskTest
{
    @Test
    void fillsTheValueBeforeEveryColumnPlaceholderWithoutChangingTheMask()
    {
        DataMask mask = new DataMask(8, 21, "custom", "COALESCE({expr}, {col})", "CONCAT('$\\', RIGHT({col}, 4))");

        assertThat(mask.expression("`card`"))
                .isEqualTo("COALESCE(CONCAT('$\\', RIGHT(`card`, 4)), `card`)");
        assertThat(mask.expression("`phone`"))
                .isEqualTo("COALESCE(CONCAT('$\\', RIGHT(`phone`, 4)), `phone`)");
        assertThat(mask.expression("`card`"))
                .isEqualTo("COALESCE(CONCAT('$\\', RIGHT(`card`, 4)), `card`)");
        assertThat(mask.transformer()).isEqualTo("COALESCE({expr}, {col})");
        assertThat(mask.value()).isEqualTo("CONCAT('$\\', RIGHT({col}, 4))");
        assertThat(mask.policyVersion()).isEqualTo(8);
        assertThat(mask.policyId()).isEqualTo(21);
        assertThat(mask.maskType()).isEqualTo("custom");
        assertThat(mask).hasToString("custom by policy 21");
    }

    @Test
    void treatsTheColumnAsLiteralTextRatherThanAReplacementPattern()
    {
        DataMask mask = new DataMask(1, 2, "hash", "SHA2({col}, 256)", null);

        assertThat(mask.expression("`price$\\code`"))
                .isEqualTo("SHA2(`price$\\code`, 256)");
    }

    @Test
    void leavesTypesWithoutATransformerToTheTargetAgent()
    {
        DataMask mask = new DataMask(4, 23, "redact", null, null);

        assertThat(mask.expression("`name`")).isNull();
        assertThat(mask.transformer()).isNull();
        assertThat(mask.value()).isNull();
    }

    @Test
    void refusesMissingAndEmptyValuesWhenTheTransformerNeedsOne()
    {
        DataMask missing = new DataMask(4, 23, "custom", "{expr}", null);
        DataMask empty = new DataMask(4, 24, "custom", "{expr}", "");

        assertThatIllegalStateException().isThrownBy(() -> missing.expression("`name`"))
                .withMessage("mask type custom of policy 23 needs a value");
        assertThatIllegalStateException().isThrownBy(() -> empty.expression("`name`"))
                .withMessage("mask type custom of policy 24 needs a value");
    }

    @Test
    void doesNotRequireAnUnusedValueForConstantOrColumnMasks()
    {
        assertThat(new DataMask(1, 2, "mask_null", "NULL", null).expression("`card`"))
                .isEqualTo("NULL");
        assertThat(new DataMask(1, 2, "mask_hash", "SHA2({col}, 256)", "").expression("`card`"))
                .isEqualTo("SHA2(`card`, 256)");
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void rejectsAnUnnamedMaskAndAnAbsentColumnEvenWithoutATransformer()
    {
        assertThatNullPointerException().isThrownBy(() -> new DataMask(1, 2, null, "NULL", null))
                .withMessage("maskType");
        DataMask mask = new DataMask(1, 2, "redact", null, null);
        assertThatNullPointerException().isThrownBy(() -> mask.expression(null)).withMessage("column");
    }
}
