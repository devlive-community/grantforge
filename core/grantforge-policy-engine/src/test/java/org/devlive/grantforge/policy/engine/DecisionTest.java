// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionTest
{
    @Test
    void namesTheOutcomeAndTheDecidingPolicy()
    {
        assertThat(Decision.allowedBy(3).allowed()).isTrue();
        assertThat(Decision.allowedBy(3)).isEqualTo(Decision.allowedBy(3)).hasSameHashCodeAs(Decision.allowedBy(3))
                .isNotEqualTo(Decision.deniedBy(3)).isNotEqualTo(Decision.allowedBy(4)).isNotEqualTo("ALLOWED");
        assertThat(Decision.deniedBy(3)).hasToString("DENIED by policy 3");
        assertThat(Decision.notDetermined()).hasToString("NOT_DETERMINED").isEqualTo(Decision.notDetermined());
        assertThat(Decision.notDetermined().hashCode()).isEqualTo(Decision.Outcome.NOT_DETERMINED.hashCode() * 31);
        assertThat(Decision.notDetermined().policyId()).isNull();
        assertThat(Decision.notDetermined()).isNotEqualTo(Decision.deniedBy(1));
    }
}
