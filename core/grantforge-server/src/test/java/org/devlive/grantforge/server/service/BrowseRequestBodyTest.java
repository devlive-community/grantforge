// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowseRequestBodyTest
{
    @Test
    void boundsTheDirectoryCursorAndPageSize()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            String path = "/" + "a".repeat(1023);
            assertThat(validator.validate(new BrowseRequestBody("path", path, null, 500))).isEmpty();
            assertThat(validator.validate(new BrowseRequestBody("path", null, null, null))).isEmpty();
            assertThat(validator.validate(new BrowseRequestBody("path", path + "a", null, 0))).extracting(problem -> problem
                    .getPropertyPath().toString()).containsExactlyInAnyOrder("directory", "pageSize");
            assertThat(validator.validate(new BrowseRequestBody(" ", null, "x".repeat(2049), 501))).extracting(problem -> problem
                    .getPropertyPath().toString()).containsExactlyInAnyOrder("resource", "cursor", "pageSize");
        }
    }
}
