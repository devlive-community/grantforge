// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthFindingTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void needsAnIssueAndACode()
    {
        assertThat(new HealthFinding(HealthIssue.UNUSED_API, 1, "api:x", null, null, null, null).resourceCode()).isEqualTo("api:x");
        assertThatThrownBy(() -> new HealthFinding(null, 1, "api:x", null, null, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HealthFinding(HealthIssue.UNUSED_API, 1, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
