// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
class LookupRequestBodyTest
{
    @Test
    void acceptsTheSamePathLengthAsPolicyResourceValues()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            String path = "/" + "a".repeat(1023);
            assertThat(validator.validate(new LookupRequestBody("path", path, Map.of(), 20))).isEmpty();
            assertThat(validator.validate(new LookupRequestBody("path", path + "a", Map.of(), 20)))
                    .singleElement().satisfies(problem -> assertThat(problem.getPropertyPath().toString()).isEqualTo("userInput"));
        }
    }

    @Test
    void leavesOutLevelsAndValuesWithoutValue()
    {
        Map<String, List<String>> context = new HashMap<>();
        context.put("database", new ArrayList<>(Arrays.asList("sales", null)));
        context.put("table", null);
        LookupRequestBody body = new LookupRequestBody("table", null, context, null);
        context.clear();
        Map<String, List<String>> copied = body.context();
        assertThat(copied).containsExactly(Map.entry("database", List.of("sales")));
        assertThat(new LookupRequestBody("database", null, null, 5).context()).isEmpty();
    }
}
