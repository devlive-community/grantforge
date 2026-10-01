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

class DependencyKindRequestTest
{
    @Test
    void requiresTheKind()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new DependencyKindRequest(DependencyKind.OPTIONAL))).isEmpty();
            assertThat(factory.getValidator().validate(new DependencyKindRequest(null))).hasSize(1);
        }
    }
}
