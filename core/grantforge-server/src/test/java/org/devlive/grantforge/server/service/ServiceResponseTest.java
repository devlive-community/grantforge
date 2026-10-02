// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.service.ServiceView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceResponseTest
{
    @Test
    void sendsTheIdAsTextAndOnlyWhichSecretsAreSet()
    {
        ServiceView view = new ServiceView(9_007_199_254_740_993L, "warehouse", "Warehouse", null, "demo", "Demo", true,
                Map.of("url", "demo://dw"), Set.of("token", "password"));
        ServiceResponse response = ServiceResponse.from(view);
        assertThat(response.id()).isEqualTo("9007199254740993");
        assertThat(response.available()).isTrue();
        assertThat(response.values()).containsExactly(Map.entry("url", "demo://dw"));
        List<String> secrets = response.secretsSet();
        assertThat(secrets).containsExactly("password", "token");
    }
}
