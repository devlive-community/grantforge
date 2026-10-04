// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoredAuthorizationRepositoryTest
{
    @Autowired
    private StoredAuthorizationRepository authorizations;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        authorizations.deleteAllInBatch();
    }

    @Test
    void storesEveryPartAndFindsItByEachToken()
    {
        authorizations.save(StoredAuthorization.create("auth-1", OAuthTestData.signedIn("c1", "a1", "r1")));
        authorizations.save(StoredAuthorization.create("auth-2", OAuthTestData.signedIn("c2", "a2", "r2")));

        assertThat(authorizations.findByAuthorizationId("auth-1")).hasValueSatisfying(found ->
                assertThat(found.content()).isEqualTo(OAuthTestData.signedIn("c1", "a1", "r1")));
        assertThat(authorizations.findByCodeHash("c2")).map(StoredAuthorization::getAuthorizationId).contains("auth-2");
        assertThat(authorizations.findByAccessHash("a1")).map(StoredAuthorization::getAuthorizationId).contains("auth-1");
        assertThat(authorizations.findByRefreshHash("r2")).map(StoredAuthorization::getAuthorizationId).contains("auth-2");
        assertThat(authorizations.findByIdTokenHash("c1")).isEmpty();
    }

    @Test
    void deletesByClientAccountAndExpiry()
    {
        authorizations.save(StoredAuthorization.create("auth-1", OAuthTestData.signedIn("c1", "a1", "r1")));

        assertThat(inTransaction(() -> authorizations.deleteExpired(OAuthTestData.NOW.plusSeconds(3600)))).isZero();
        assertThat(inTransaction(() -> authorizations.deleteByAccount(9))).isZero();
        assertThat(inTransaction(() -> authorizations.deleteByAccount(42))).isOne();
        authorizations.save(StoredAuthorization.create("auth-2", OAuthTestData.signedIn("c2", "a2", "r2")));
        assertThat(inTransaction(() -> authorizations.deleteByClient("7"))).isOne();
        authorizations.save(StoredAuthorization.create("auth-3", OAuthTestData.signedIn("c3", "a3", "r3")));
        assertThat(inTransaction(() -> authorizations.deleteExpired(OAuthTestData.NOW.plusSeconds(3601)))).isOne();
    }

    @Test
    void authorizationIdsAreUnique()
    {
        authorizations.saveAndFlush(StoredAuthorization.create("auth-1", OAuthTestData.signedIn("c1", "a1", "r1")));

        assertThatThrownBy(() -> authorizations.saveAndFlush(StoredAuthorization.create("auth-1", OAuthTestData.signedIn("c2", "a2", "r2"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int inTransaction(Supplier<Integer> work)
    {
        return requireNonNull(new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }
}
