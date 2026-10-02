// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyRequestTest
{
    @Test
    void requiresTheResource()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new DependencyRequest("7", null))).isEmpty();
            assertThat(factory.getValidator().validate(new DependencyRequest(" ", DependencyKind.OPTIONAL))).hasSize(1);
            assertThat(factory.getValidator().validate(new DependencyRequest("1".repeat(21), null))).hasSize(1);
        }
    }
}
