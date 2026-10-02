// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceUpdateRequestTest
{
    @Test
    void requiresCodeAndNameAndDefaultsTheFlags()
    {
        ResourceUpdateRequest page = new ResourceUpdateRequest("users", "Users", "List", null, null, false, null);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(page)).isEmpty();
            assertThat(factory.getValidator().validate(new ResourceUpdateRequest(null, " ", null, null, null, null, null)))
                    .hasSize(2);
        }
        assertThat(page.details()).isEqualTo(new ResourceDetails("Users", "List", null, true, false, DenyMode.HIDE));
    }
}
