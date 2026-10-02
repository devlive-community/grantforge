// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRequestTest
{
    @Test
    void requiresACodeAndAName()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new RoleRequest("auditors", "Auditors", null))).isEmpty();
            assertThat(factory.getValidator().validate(new RoleRequest(" ", "x".repeat(129), "x".repeat(501)))).hasSize(3);
        }
    }
}
