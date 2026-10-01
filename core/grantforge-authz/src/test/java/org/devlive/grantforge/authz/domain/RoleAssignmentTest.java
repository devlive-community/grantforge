// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAssignmentTest
{
    private static final Instant MAY = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant JUNE = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void appliesWithinItsValidity()
    {
        RoleAssignment assignment = RoleAssignment.create(1, SubjectType.USER, 2, new RoleAssignment.Terms(MAY, JUNE, true));

        assertThat(assignment.getRoleId()).isEqualTo(1);
        assertThat(assignment.getSubjectType()).isEqualTo(SubjectType.USER);
        assertThat(assignment.getSubjectId()).isEqualTo(2);
        // Only departments have sub-departments.
        assertThat(assignment.getTerms()).isEqualTo(new RoleAssignment.Terms(MAY, JUNE, false));
        assertThat(assignment.isValidAt(MAY.minusSeconds(1))).isFalse();
        assertThat(assignment.isValidAt(MAY)).isTrue();
        assertThat(assignment.isValidAt(JUNE.minusSeconds(1))).isTrue();
        assertThat(assignment.isValidAt(JUNE)).isFalse();

        assignment.change(RoleAssignment.Terms.UNLIMITED);
        assertThat(assignment.isValidAt(Instant.EPOCH)).isTrue();
        assertThat(RoleAssignment.create(1, SubjectType.ORG_UNIT, 3, new RoleAssignment.Terms(null, JUNE, true)).getTerms()
                .includeSubUnits()).isTrue();
    }

    @Test
    void refusesValiditiesEndingBeforeTheyStart()
    {
        assertThatThrownBy(() -> RoleAssignment.create(1, SubjectType.USER, 2, new RoleAssignment.Terms(JUNE, MAY, false)))
                .hasMessageContaining("end after it starts");
        assertThatThrownBy(() -> RoleAssignment.create(1, SubjectType.USER, 2, new RoleAssignment.Terms(MAY, MAY, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
