// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrgUnitRequestTest
{
    @Test
    void requiresACodeAndAName()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new OrgUnitRequest(null, "hq", "HQ"))).isEmpty();
            assertThat(factory.getValidator().validate(new OrgUnitRequest("1".repeat(21), " ", "x".repeat(129))))
                    .hasSize(3);
        }
    }
}
