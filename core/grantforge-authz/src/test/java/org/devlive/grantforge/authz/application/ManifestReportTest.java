// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestReportTest
{
    @Test
    void holdsTheCounts()
    {
        assertThat(new ManifestReport(9, 1, 2, 3, 4)).extracting(ManifestReport::resources, ManifestReport::created,
                ManifestReport::updated, ManifestReport::dependenciesAdded, ManifestReport::dependenciesRemoved)
                .containsExactly(9, 1, 2, 3, 4);
    }
}
