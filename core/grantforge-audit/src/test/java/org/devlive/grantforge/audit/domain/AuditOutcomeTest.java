// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditOutcomeTest
{
    @Test
    void namesFitTheOutcomeColumn()
    {
        // The outcome column is VARCHAR(16).
        assertThat(AuditOutcome.values()).extracting(Enum::name).containsExactly("SUCCESS", "FAILURE");
    }
}
