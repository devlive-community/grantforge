// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaStatusTest
{
    @Test
    void exposesItsComponents()
    {
        MfaStatus status = new MfaStatus(true, 4);

        assertThat(status.enabled()).isTrue();
        assertThat(status.recoveryCodesLeft()).isEqualTo(4);
    }
}
