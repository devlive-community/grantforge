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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApiEndpointRepositoryTest
{
    private static final ApiEndpoint.Declaration OPEN = new ApiEndpoint.Declaration("A#b", EndpointAccess.PUBLIC, null);

    @Autowired
    private ApiEndpointRepository endpoints;

    @AfterEach
    void deleteRows()
    {
        endpoints.deleteAllInBatch();
    }

    @Test
    void listsByPathAndMethodAndKeepsRoutesUnique()
    {
        endpoints.save(ApiEndpoint.discover("POST", "/api/v1/users", OPEN, null, Instant.EPOCH));
        endpoints.save(ApiEndpoint.discover("GET", "/api/v1/users", OPEN, null, Instant.EPOCH));
        endpoints.save(ApiEndpoint.discover("GET", "/api/v1/groups", OPEN, null, Instant.EPOCH));

        assertThat(endpoints.findOrdered()).extracting(endpoint -> endpoint.getHttpMethod() + " " + endpoint.getPathPattern())
                .containsExactly("GET /api/v1/groups", "GET /api/v1/users", "POST /api/v1/users");
        assertThatThrownBy(() -> endpoints.saveAndFlush(ApiEndpoint.discover("GET", "/api/v1/users", OPEN, null, Instant.EPOCH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
