// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentRequestTest
{
    @Test
    void requiresTheSubject()
    {
        AssignmentRequest group = new AssignmentRequest(SubjectType.GROUP, "7", null, null, null);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(group)).isEmpty();
            assertThat(factory.getValidator().validate(new AssignmentRequest(null, " ", null, null, null))).hasSize(2);
        }
        assertThat(group.terms()).isEqualTo(RoleAssignment.Terms.UNLIMITED);
    }
}
