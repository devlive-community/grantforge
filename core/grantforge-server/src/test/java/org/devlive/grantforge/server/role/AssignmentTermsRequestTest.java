// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentTermsRequestTest
{
    @Test
    void omittedFlagsMeanTheDepartmentAlone()
    {
        Instant end = Instant.parse("2026-12-31T00:00:00Z");

        assertThat(new AssignmentTermsRequest(null, end, null).terms()).isEqualTo(new RoleAssignment.Terms(null, end, false));
        assertThat(new AssignmentTermsRequest(null, null, true).terms().includeSubUnits()).isTrue();
    }
}
