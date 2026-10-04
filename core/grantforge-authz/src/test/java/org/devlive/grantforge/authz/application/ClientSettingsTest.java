// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ClientGrant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientSettingsTest
{
    @Test
    void copiesItsCollections()
    {
        List<String> uris = new ArrayList<>(List.of("https://a.example/cb"));
        ClientSettings settings = new ClientSettings("CRM", uris, Set.of("openid"), Set.of(ClientGrant.AUTHORIZATION_CODE),
                Duration.ofMinutes(15), Duration.ofDays(30), true);
        uris.clear();

        assertThat(settings.redirectUris()).containsExactly("https://a.example/cb");
        assertThatThrownBy(() -> settings.scopes().add("email")).isInstanceOf(UnsupportedOperationException.class);
    }
}
