// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationRequestTest
{
    @Test
    void validatesTheNameAndKeepsThePasswordOutOfLogs()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new RegistrationRequest(" alice ", "pw", null))).isEmpty();
            assertThat(factory.getValidator().validate(new RegistrationRequest("a b", " ", "x".repeat(129)))).hasSize(3);
        }
        assertThat(new RegistrationRequest("alice", "secret password", null).toString()).doesNotContain("secret");
    }
}
