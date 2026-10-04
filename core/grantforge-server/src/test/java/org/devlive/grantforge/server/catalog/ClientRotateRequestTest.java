// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRotateRequestTest
{
    @Test
    void limitsTheGracePeriodToAWeek()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new ClientRotateRequest(null))).isEmpty();
            assertThat(factory.getValidator().validate(new ClientRotateRequest(168))).isEmpty();
            assertThat(factory.getValidator().validate(new ClientRotateRequest(169))).hasSize(1);
            assertThat(factory.getValidator().validate(new ClientRotateRequest(-1))).hasSize(1);
        }
    }
}
