// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionRequestTest
{
    @Test
    void validatesAndDefaultsTheSortOrder()
    {
        assertThat(new PositionRequest("cfo", "CFO", null, null).order()).isZero();
        assertThat(new PositionRequest("cfo", "CFO", null, 4).order()).isEqualTo(4);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new PositionRequest("cfo", "CFO", null, 4))).isEmpty();
            assertThat(factory.getValidator().validate(new PositionRequest(" ", "", "x".repeat(513), -1))).hasSize(4);
        }
    }
}
