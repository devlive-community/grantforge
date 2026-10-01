// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusTest
{
    @Test
    void namesFitTheStatusColumn()
    {
        // The status column is VARCHAR(16).
        assertThat(Arrays.stream(AccountStatus.values()).map(Enum::name)).allSatisfy(name -> assertThat(name).hasSizeLessThanOrEqualTo(16));
    }
}
