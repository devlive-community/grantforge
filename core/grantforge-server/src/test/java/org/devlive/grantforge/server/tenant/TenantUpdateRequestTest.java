// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantUpdateRequestTest
{
    @Test
    void requiresANameOfAtMost128Characters()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new TenantUpdateRequest("Acme"))).isEmpty();
            assertThat(factory.getValidator().validate(new TenantUpdateRequest(" "))).hasSize(1);
            assertThat(factory.getValidator().validate(new TenantUpdateRequest("x".repeat(129)))).hasSize(1);
        }
    }
}
