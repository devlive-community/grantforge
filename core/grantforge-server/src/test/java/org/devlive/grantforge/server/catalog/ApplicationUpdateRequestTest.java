// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationUpdateRequestTest
{
    @Test
    void requiresAName()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new ApplicationUpdateRequest("CRM", null))).isEmpty();
            assertThat(factory.getValidator().validate(new ApplicationUpdateRequest(null, "x".repeat(501)))).hasSize(2);
        }
    }
}
