// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupRequestTest
{
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close()
    {
        FACTORY.close();
    }

    @Test
    void validRequestsPass()
    {
        assertThat(VALIDATOR.validate(new SetupRequest("t", null, " admin@corp ", "secret", null))).isEmpty();
    }

    @Test
    void missingAndMalformedFieldsAreReported()
    {
        assertThat(VALIDATOR.validate(new SetupRequest(null, "x".repeat(129), "a b", " ", "y".repeat(129))).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString))
                .containsExactlyInAnyOrder("token", "tenantName", "username", "password", "displayName");
    }

    @Test
    void toStringHidesSecrets()
    {
        assertThat(new SetupRequest("the-token", "Acme", "admin", "the-password", null).toString())
                .contains("Acme", "admin").doesNotContain("the-token", "the-password");
    }
}
