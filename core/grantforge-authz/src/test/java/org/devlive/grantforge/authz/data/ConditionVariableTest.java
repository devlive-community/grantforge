// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionVariableTest
{
    @Test
    void knowsItsNameKindAndWhetherItIsAList()
    {
        assertThat(ConditionVariable.of("subject.groupCodes")).contains(ConditionVariable.SUBJECT_GROUP_CODES);
        assertThat(ConditionVariable.of("subject.password")).isEmpty();
        assertThat(ConditionVariable.SUBJECT_GROUP_CODES.type()).isEqualTo(DataFieldType.TEXT);
        assertThat(ConditionVariable.SUBJECT_GROUP_CODES.list()).isTrue();
        assertThat(ConditionVariable.NOW.key()).isEqualTo("now");
        assertThat(ConditionVariable.NOW.list()).isFalse();
    }
}
