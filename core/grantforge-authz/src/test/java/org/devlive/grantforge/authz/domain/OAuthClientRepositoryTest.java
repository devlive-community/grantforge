// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OAuthClientRepositoryTest
{
    @Autowired
    private OAuthClientRepository clients;

    @Autowired
    private ApplicationRepository applications;

    @AfterEach
    void deleteRows()
    {
        clients.deleteAllInBatch();
        applications.deleteAllInBatch();
    }

    @Test
    void findsClientsByApplicationAndClientId()
    {
        long crm = applications.save(Application.create("crm", "CRM", null)).requireId();
        long erp = applications.save(Application.create("erp", "ERP", null)).requireId();
        clients.save(client(crm, "gf_1", Instant.EPOCH));
        clients.save(client(crm, "gf_2", Instant.EPOCH.plusSeconds(1)));

        assertThat(clients.findByApplicationIdOrderByCreatedAtAscIdAsc(crm)).extracting(OAuthClient::getClientId)
                .containsExactly("gf_1", "gf_2");
        assertThat(clients.findByClientId("gf_2")).hasValueSatisfying(found -> {
            assertThat(found.getRedirectUris()).containsExactly("https://crm.example/cb");
            assertThat(found.getGrants()).containsExactly(ClientGrant.AUTHORIZATION_CODE);
        });
        assertThat(clients.existsByApplicationId(crm)).isTrue();
        assertThat(clients.existsByApplicationId(erp)).isFalse();
    }

    @Test
    void clientIdsAreUnique()
    {
        long crm = applications.save(Application.create("crm", "CRM", null)).requireId();
        clients.saveAndFlush(client(crm, "gf_1", Instant.EPOCH));

        assertThatThrownBy(() -> clients.saveAndFlush(client(crm, "gf_1", Instant.EPOCH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static OAuthClient client(long applicationId, String clientId, Instant now)
    {
        OAuthClient client = OAuthClient.create(applicationId, clientId, ClientType.PUBLIC, null, now);
        client.configure(clientId, List.of("https://crm.example/cb"), Set.of("openid"), Set.of(ClientGrant.AUTHORIZATION_CODE),
                Duration.ofMinutes(15), Duration.ofDays(30), true);
        return client;
    }
}
