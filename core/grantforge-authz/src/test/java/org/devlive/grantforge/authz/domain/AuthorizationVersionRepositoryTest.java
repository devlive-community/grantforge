// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorizationVersionRepositoryTest
{
    @Autowired
    private AuthorizationVersionRepository counters;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteTenantCounters()
    {
        counters.findAll().stream().filter(counter -> !"catalog".equals(counter.getScope())).forEach(counters::delete);
    }

    private int raise(String scope)
    {
        Integer raised = new TransactionTemplate(transactionManager).execute(status -> counters.raise(scope, Instant.EPOCH));
        return raised == null ? -1 : raised;
    }

    @Test
    void theCatalogCounterExistsFromTheStartAndCountersRise()
    {
        Optional<AuthorizationVersion> catalog = counters.findById("catalog");
        assertThat(catalog).isPresent();
        long before = catalog.orElseThrow().getVersion();
        assertThat(raise("catalog")).isEqualTo(1);
        assertThat(counters.findById("catalog").orElseThrow().getVersion()).isEqualTo(before + 1);

        assertThat(raise("tenant:7")).isZero();
        counters.save(AuthorizationVersion.first("tenant:7", Instant.EPOCH));
        assertThat(raise("tenant:7")).isEqualTo(1);
        assertThat(counters.findById("tenant:7").orElseThrow().getVersion()).isEqualTo(2);
    }
}
