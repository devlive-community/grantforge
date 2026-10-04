// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SodConstraintTest
{
    @Test
    void keepsItsSettings()
    {
        SodConstraint constraint = SodConstraint.create("payments");
        assertThat(constraint.getMaxRoles()).isOne();
        assertThat(constraint.getMode()).isEqualTo(SodMode.ENFORCE);
        assertThat(constraint.isEnabled()).isTrue();

        constraint.configure("Payments", "Who pays does not approve", 2, SodMode.REPORT, false);

        assertThat(constraint.getCode()).isEqualTo("payments");
        assertThat(constraint.getName()).isEqualTo("Payments");
        assertThat(constraint.getDescription()).isEqualTo("Who pays does not approve");
        assertThat(constraint.getMaxRoles()).isEqualTo(2);
        assertThat(constraint.getMode()).isEqualTo(SodMode.REPORT);
        assertThat(constraint.isEnabled()).isFalse();
        assertThatThrownBy(() -> constraint.configure("x", null, 0, SodMode.ENFORCE, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
