// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiReviewRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the field gives null
    void limitsTheIds()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new ApiReviewRequest(List.of("1", "2")))).isEmpty();
            assertThat(new ApiReviewRequest(null).endpointIds()).isEmpty();
            assertThat(factory.getValidator().validate(new ApiReviewRequest(Collections.nCopies(1001, "1")))).hasSize(1);
            assertThat(factory.getValidator().validate(new ApiReviewRequest(List.of("1".repeat(21))))).hasSize(1);
        }
        assertThat(new ApiReviewRequest(List.of(" 7 ")).ids()).containsExactly(7L);
    }
}
