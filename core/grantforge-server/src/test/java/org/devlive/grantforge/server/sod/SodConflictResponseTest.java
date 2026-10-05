// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.application.SodConflict;
import org.devlive.grantforge.authz.application.SodConstraintView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SodConflictResponseTest
{
    @Test
    void flattensTheConstraintAndTheAccount()
    {
        RoleView payer = new RoleView(7, "payer", "Payer", null, RoleType.CUSTOM, true);
        SodConflictResponse response = SodConflictResponse.from(new SodConflict(new SodConstraintView(3, "payments", "Payments", null, List.of(payer),
                1, SodMode.REPORT, true), new Subject(SubjectType.USER, 9, "Alice", "alice"), List.of(payer)));

        assertThat(response).extracting(SodConflictResponse::constraintId, SodConflictResponse::constraintName, SodConflictResponse::mode,
                SodConflictResponse::accountId, SodConflictResponse::username).containsExactly("3", "Payments", SodMode.REPORT, "9", "alice");
    }
}
