// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessRequestStatusTest
{
    @Test
    void namesEveryStage()
    {
        assertThat(AccessRequestStatus.values()).containsExactly(AccessRequestStatus.PENDING, AccessRequestStatus.APPROVED,
                AccessRequestStatus.REJECTED, AccessRequestStatus.CANCELLED, AccessRequestStatus.EXPIRED, AccessRequestStatus.REVOKED);
    }
}
