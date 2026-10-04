// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCheckResponseTest
{
    @Test
    void copiesTheAnswers()
    {
        Map<String, Boolean> answers = new HashMap<>(Map.of("orders.read", true));
        OpenCheckResponse response = new OpenCheckResponse(answers);
        answers.put("orders.delete", false);

        assertThat(response.permissions()).containsOnlyKeys("orders.read");
    }
}
