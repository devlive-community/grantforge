// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectTypeTest
{
    @Test
    void namesFitTheSubjectColumn()
    {
        // Stored by name in the VARCHAR(16) subject_type column.
        assertThat(SubjectType.values()).extracting(Enum::name).containsExactly("USER", "GROUP", "ORG_UNIT", "POSITION");
    }
}
