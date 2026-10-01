// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrgMemberTest
{
    @Test
    void recordsTheAccountDepartmentAndWhetherItIsPrimary()
    {
        OrgMember member = OrgMember.of(1, 2, true);

        assertThat(member.getAccountId()).isOne();
        assertThat(member.getOrgUnitId()).isEqualTo(2);
        assertThat(member.isPrimaryUnit()).isTrue();
        assertThat(OrgMember.of(1, 3, false).isPrimaryUnit()).isFalse();
    }
}
