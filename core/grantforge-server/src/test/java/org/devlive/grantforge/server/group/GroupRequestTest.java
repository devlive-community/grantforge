// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupRequestTest
{
    @Test
    void requiresACodeAndAName()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new GroupRequest("ops", "Ops", null))).isEmpty();
            assertThat(factory.getValidator().validate(new GroupRequest(" ", "", "x".repeat(513)))).hasSize(3);
        }
    }
}
