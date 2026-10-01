// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileRequestTest
{
    @Test
    void limitsTheLengthOfBothFields()
    {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(new ProfileRequest(null, null))).isEmpty();
            assertThat(validator.validate(new ProfileRequest("x".repeat(128), "a@" + "b".repeat(252)))).isEmpty();
            assertThat(validator.validate(new ProfileRequest("x".repeat(129), "a@" + "b".repeat(253)))).hasSize(2);
        }
    }
}
