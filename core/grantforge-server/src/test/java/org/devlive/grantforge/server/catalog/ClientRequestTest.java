// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRequestTest
{
    @Test
    void requiresATypeAndValidSettings()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ClientSettingsRequest good = new ClientSettingsRequest("CRM", null, null, Set.of(ClientGrant.CLIENT_CREDENTIALS), null, null, null);
            assertThat(factory.getValidator().validate(new ClientRequest(ClientType.PUBLIC, good))).isEmpty();
            assertThat(factory.getValidator().validate(new ClientRequest(null, null))).hasSize(2);
            assertThat(factory.getValidator().validate(new ClientRequest(ClientType.PUBLIC,
                    new ClientSettingsRequest(" ", null, null, Set.of(ClientGrant.CLIENT_CREDENTIALS), null, null, null)))).hasSize(1);
        }
    }
}
