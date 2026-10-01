// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsoleSessionRepositoryTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private ConsoleSessionRepository sessions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;
    private long alice;
    private long bob;

    @BeforeEach
    void createAccounts()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", NOW).withDisplayName("Alice A")).requireId());
        bob = inTenant(() -> accounts.save(UserAccount.create("bob", "h", NOW)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            sessions.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status ->
                action.get()));
    }

    private void start(String sessionId, long account, Instant at)
    {
        inTenant(() -> sessions.save(ConsoleSession.start(sessionId, account, null, null, at)));
    }

    @Test
    void listsActiveSessionsOfTheTenantWithTheirOwners()
    {
        start("old", alice, NOW.minusSeconds(3600));
        start("a1", alice, NOW.minusSeconds(60));
        start("b1", bob, NOW);

        Page<ConsoleSessionEntry> page = inTenant(() -> sessions.findActive(NOW.minusSeconds(600), PageRequest.of(0, 10)));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(entry -> entry.session().getSessionId()).containsExactly("b1", "a1");
        assertThat(page.getContent()).extracting(ConsoleSessionEntry::username, ConsoleSessionEntry::displayName)
                .containsExactly(tuple("bob", null),
                        tuple("alice", "Alice A"));
        long otherTenant = tenants.save(Tenant.create("other", "Other")).requireId();
        assertThat(TenantContext.callInTenant(otherTenant, () -> sessions.findActive(NOW.minusSeconds(600),
                PageRequest.of(0, 10)).getTotalElements())).isZero();
    }

    @Test
    void touchesForgetsAndExpiresSessions()
    {
        start("a1", alice, NOW.minusSeconds(3600));
        start("a2", alice, NOW.minusSeconds(3600));
        start("a3", alice, NOW.minusSeconds(3600));

        assertThat(inTenant(() -> sessions.touch("a1", NOW))).isEqualTo(1);
        assertThat(inTenant(() -> sessions.touch("missing", NOW))).isZero();
        assertThat(inTenant(() -> sessions.findBySessionId("a1")).map(ConsoleSession::getLastSeenAt)).contains(NOW);
        assertThat(inTenant(() -> sessions.findByAccountIdAndLastSeenAtAfterOrderByLastSeenAtDescIdDesc(alice,
                NOW.minusSeconds(60)))).extracting(ConsoleSession::getSessionId).containsExactly("a1");

        assertThat(inTenant(() -> sessions.deleteBySessionId("a2"))).isEqualTo(1);
        assertThat(inTenant(() -> sessions.deleteExpired(NOW.minusSeconds(60)))).isEqualTo(1);
        assertThat(inTenant(() -> sessions.findByAccountId(alice))).extracting(ConsoleSession::getSessionId)
                .containsExactly("a1");
    }

    @Test
    void sessionIdsAreUnique()
    {
        start("a1", alice, NOW);

        assertThatThrownBy(() -> start("a1", bob, NOW)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
