// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncReportTest
{
    @Test
    void holdsTheCounts()
    {
        assertThat(new SyncReport(10, 2, 1, 3, 4)).extracting(SyncReport::endpoints, SyncReport::added, SyncReport::changed,
                SyncReport::removed, SyncReport::permissions).containsExactly(10, 2, 1, 3, 4);
    }
}
