// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import org.devlive.grantforge.authz.application.SodConstraintCommand;
import org.devlive.grantforge.authz.domain.SodMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SodConstraintRequestTest
{
    @Test
    void enforcesOneRoleUnlessSaidOtherwise()
    {
        SodConstraintCommand command = new SodConstraintRequest(" payments ", "Payments", null, List.of("1", " 2 ", "1"), null, null, null).command();

        assertThat(command.code()).isEqualTo("payments");
        assertThat(command.roleIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(command.maxRoles()).isOne();
        assertThat(command.mode()).isEqualTo(SodMode.ENFORCE);
        assertThat(command.enabled()).isTrue();
        SodConstraintCommand report = new SodConstraintRequest(null, "x", "d", null, 2, SodMode.REPORT, false).command();
        assertThat(report.code()).isEmpty();
        assertThat(report.roleIds()).isEmpty();
        assertThat(report.mode()).isEqualTo(SodMode.REPORT);
        assertThat(report.enabled()).isFalse();
    }
}
