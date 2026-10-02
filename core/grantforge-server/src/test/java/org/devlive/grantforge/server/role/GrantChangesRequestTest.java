// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrantChangesRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the changes gives null
    void requiresTheApplicationAndValidatesEachChange()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new GrantChangesRequest("1", List.of()))).isEmpty();
            assertThat(factory.getValidator().validate(new GrantChangesRequest(null, List.of(new GrantChangeRequest(null, null, null)))))
                    .hasSize(2);
        }
        assertThat(new GrantChangesRequest("1", null).changes()).isEmpty();
    }
}
