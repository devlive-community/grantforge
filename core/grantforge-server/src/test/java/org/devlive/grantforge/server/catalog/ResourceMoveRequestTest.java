// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceMoveRequestTest
{
    @Test
    void limitsTheParentId()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new ResourceMoveRequest(null, 0))).isEmpty();
            assertThat(factory.getValidator().validate(new ResourceMoveRequest("1".repeat(21), 0))).hasSize(1);
        }
    }
}
