// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.application.GrantChange;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrantChangeRequestTest
{
    @Test
    void requiresTheResourceAndConverts()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new GrantChangeRequest(" ", null, null))).hasSize(1);
        }
        assertThat(new GrantChangeRequest(" 7 ", GrantEffect.ALLOW, null).change()).isEqualTo(new GrantChange(7, GrantEffect.ALLOW, null));
    }
}
