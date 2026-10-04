// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodesResponseTest
{
    @Test
    void copiesTheCodesAndHidesThem()
    {
        List<String> codes = new ArrayList<>(List.of("abcde-fghij"));
        RecoveryCodesResponse response = new RecoveryCodesResponse(codes);
        codes.clear();

        assertThat(response.codes()).containsExactly("abcde-fghij");
        assertThat(response.toString()).isEqualTo("RecoveryCodesResponse[1]");
    }
}
