// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RetiredTokenRepositoryTest
{
    @Autowired
    private RetiredTokenRepository retired;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        retired.deleteAllInBatch();
    }

    @Test
    void findsTokensAndForgetsThemByAuthorizationOrExpiry()
    {
        retired.save(RetiredToken.of("h1", "auth-1", Instant.EPOCH.plusSeconds(10)));
        retired.save(RetiredToken.of("h2", "auth-1", Instant.EPOCH.plusSeconds(20)));
        retired.save(RetiredToken.of("h3", "auth-2", Instant.EPOCH.plusSeconds(30)));

        assertThat(retired.findByTokenHash("h3")).map(RetiredToken::getAuthorizationId).contains("auth-2");
        assertThat(inTransaction(() -> retired.deleteExpired(Instant.EPOCH.plusSeconds(15)))).isOne();
        assertThat(inTransaction(() -> retired.deleteByAuthorization("auth-1"))).isOne();
        assertThat(retired.findAll()).extracting(RetiredToken::getTokenHash).containsExactly("h3");
    }

    private int inTransaction(Supplier<Integer> work)
    {
        return requireNonNull(new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }
}
