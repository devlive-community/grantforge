// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPositionTest
{
    @Test
    void recordsTheAccountAndPosition()
    {
        AccountPosition held = AccountPosition.of(1, 2);

        assertThat(held.getAccountId()).isOne();
        assertThat(held.getPositionId()).isEqualTo(2);
    }
}
