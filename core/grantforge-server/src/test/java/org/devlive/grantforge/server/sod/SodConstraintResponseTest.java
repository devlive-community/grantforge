// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.application.SodConstraintView;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SodMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SodConstraintResponseTest
{
    @Test
    void writesIdsAsText()
    {
        SodConstraintResponse response = SodConstraintResponse.from(new SodConstraintView(3, "payments", "Payments", null,
                List.of(new RoleView(7, "payer", "Payer", null, RoleType.CUSTOM, true)), 1, SodMode.ENFORCE, true));

        assertThat(response.id()).isEqualTo("3");
        assertThat(response.roles()).extracting(role -> role.id()).containsExactly("7");
    }
}
