// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrgUnitMoveRequestTest
{
    @Test
    void acceptsRootsAndParents()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new OrgUnitMoveRequest(null, 0))).isEmpty();
            assertThat(factory.getValidator().validate(new OrgUnitMoveRequest("12", 3))).isEmpty();
            assertThat(factory.getValidator().validate(new OrgUnitMoveRequest("1".repeat(21), 3))).hasSize(1);
        }
    }
}
