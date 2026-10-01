// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberBatchRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the list passes null
    void copiesParsesAndLimitsTheIds()
    {
        List<String> ids = new ArrayList<>(List.of(" 7 ", "8"));
        MemberBatchRequest request = new MemberBatchRequest(ids);
        ids.clear();

        assertThat(request.ids()).containsExactly(7L, 8L);
        assertThat(new MemberBatchRequest(null).accountIds()).isEmpty();
        assertThatThrownBy(() -> new MemberBatchRequest(List.of("x")).ids()).isInstanceOf(GrantForgeException.class);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new MemberBatchRequest(List.of()))).hasSize(1);
            assertThat(factory.getValidator().validate(new MemberBatchRequest(Collections.nCopies(501, "1")))).hasSize(1);
        }
    }
}
