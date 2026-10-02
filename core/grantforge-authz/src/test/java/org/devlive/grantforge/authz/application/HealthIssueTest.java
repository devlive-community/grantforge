// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthIssueTest
{
    @Test
    void grantIssuesComeFirstThenButtonsApisAndDependencies()
    {
        // Reports are ordered by issue, so the order is what the check-up page shows.
        assertThat(HealthIssue.values()).containsExactly(HealthIssue.GRANT_ON_DISABLED, HealthIssue.GRANT_ON_RETIRED_API,
                HealthIssue.GRANT_EXPIRED, HealthIssue.ACTION_WITHOUT_API, HealthIssue.UNUSED_API,
                HealthIssue.DEPENDENCY_ON_DISABLED, HealthIssue.DEPENDENCY_ON_RETIRED_API);
    }
}
