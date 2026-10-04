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
class SigningKeyRepositoryTest
{
    @Autowired
    private SigningKeyRepository keys;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        keys.deleteAllInBatch();
    }

    @Test
    void listsNewestFirstAndDeletesLongRetiredKeys()
    {
        SigningKeyRecord old = SigningKeyRecord.create("k1", "RS256", "pub1", "x".repeat(3000), Instant.EPOCH);
        old.retire(Instant.EPOCH.plusSeconds(100));
        keys.save(old);
        keys.save(SigningKeyRecord.create("k2", "RS256", "pub2", "sealed2", Instant.EPOCH.plusSeconds(100)));

        assertThat(keys.findAllByOrderByActivatedAtDescIdDesc()).extracting(SigningKeyRecord::getKeyId).containsExactly("k2", "k1");
        // Private keys exceed the portable string size.
        assertThat(keys.findAllByOrderByActivatedAtDescIdDesc().get(1).getPrivateKey()).hasSize(3000);
        assertThat(inTransaction(() -> keys.deleteRetiredBefore(Instant.EPOCH.plusSeconds(100)))).isZero();
        assertThat(inTransaction(() -> keys.deleteRetiredBefore(Instant.EPOCH.plusSeconds(101)))).isOne();
        assertThat(keys.findAll()).extracting(SigningKeyRecord::getKeyId).containsExactly("k2");
    }

    private int inTransaction(Supplier<Integer> work)
    {
        return requireNonNull(new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }
}
