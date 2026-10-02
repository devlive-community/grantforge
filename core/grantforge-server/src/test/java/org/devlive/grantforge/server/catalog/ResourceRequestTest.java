// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceRequestTest
{
    @Test
    void requiresTypeCodeAndNameAndDefaultsTheFlags()
    {
        ResourceRequest page = new ResourceRequest(null, ResourceType.PAGE, "users", "Users", null, "/admin/users", null,
                null, null);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(page)).isEmpty();
            assertThat(factory.getValidator().validate(new ResourceRequest("1".repeat(21), null, " ", null, "x".repeat(501),
                    "/".repeat(256), true, true, DenyMode.HIDE))).hasSize(6);
        }
        assertThat(page.details()).isEqualTo(new ResourceDetails("Users", null, "/admin/users", true, true, DenyMode.HIDE));
        assertThat(new ResourceRequest(null, ResourceType.ACTION, "export", "Export", null, null, false, false,
                DenyMode.DISABLE).details()).isEqualTo(new ResourceDetails("Export", null, null, false, false, DenyMode.DISABLE));
    }
}
