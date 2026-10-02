// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectKindTest
{
    @Test
    void itemsNameUsersGroupsAndRoles()
    {
        assertThat(SubjectKind.values()).containsExactly(SubjectKind.USER, SubjectKind.GROUP, SubjectKind.ROLE);
    }
}
