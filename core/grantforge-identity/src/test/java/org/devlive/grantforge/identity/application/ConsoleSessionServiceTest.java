// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.ConsoleSession;
import org.devlive.grantforge.identity.domain.ConsoleSessionRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({IdentityConfiguration.class, ConsoleSessionService.class, AuthenticationServiceTest.TestClock.class})
@TestPropertySource(properties = {"grantforge.security.sessions.max-per-account=2", "spring.session.timeout=30m"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsoleSessionServiceTest
{
    private static final Instant START = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private ConsoleSessionService service;

    @Autowired
    private ConsoleSessionRepository sessions;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private SessionTerminator terminator;

    @Autowired
    private AuthenticationServiceTest.TestClock clock;

    private long tenant;
    private long admin;
    private long alice;

    @BeforeEach
    void createAccounts()
    {
        clock.set(START);
        recorded().clear();
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", START).markSystemAccount()).requireId());
        alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", START).withDisplayName("Alice A"))
                .requireId());
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

    private RecordingSessionTerminator recorded()
    {
        return (RecordingSessionTerminator) terminator;
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action);
    }

    private void inTenant(Runnable action)
    {
        TenantContext.runInTenant(tenant, action);
    }

    private void later(Duration duration)
    {
        clock.set(clock.testClock().instant().plus(duration));
    }

    private long idOf(String sessionId)
    {
        return inTenant(() -> sessions.findBySessionId(sessionId).orElseThrow().requireId());
    }

    private static CommonErrorCode codeOf(Throwable error)
    {
        return (CommonErrorCode) ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void listsOwnSessionsMarkingTheCurrentOne()
    {
        inTenant(() -> service.start("s1", alice, "10.0.0.1", "Firefox"));
        later(Duration.ofMinutes(1));
        inTenant(() -> service.start("s2", alice, null, null));

        List<ActiveSession> own = inTenant(() -> service.listOwn(alice, "s1"));

        assertThat(own).extracting(ActiveSession::current).containsExactly(false, true);
        assertThat(own.get(1)).extracting(ActiveSession::username, ActiveSession::displayName, ActiveSession::clientIp,
                ActiveSession::userAgent, ActiveSession::signedInAt).containsExactly("alice", "Alice A", "10.0.0.1",
                "Firefox", START);
        assertThat(inTenant(() -> service.listOwn(-1, null))).isEmpty();
    }

    @Test
    void signingInForgetsExpiredSessionsAndEndsSessionsBeyondTheLimit()
    {
        inTenant(() -> service.start("expired", admin, null, null));
        later(Duration.ofMinutes(40));
        inTenant(() -> service.start("s1", alice, null, null));
        later(Duration.ofMinutes(1));
        inTenant(() -> service.start("s2", alice, null, null));
        assertThat(recorded().sessions()).isEmpty();

        later(Duration.ofMinutes(1));
        inTenant(() -> service.start("s3", alice, null, null));

        assertThat(recorded().sessions()).containsExactly("s1");
        assertThat(inTenant(() -> sessions.findAll())).extracting(ConsoleSession::getSessionId)
                .containsExactlyInAnyOrder("s2", "s3");
    }

    @Test
    void activityKeepsSessionsListedAndReindexesMissingOnes()
    {
        inTenant(() -> service.start("s1", alice, null, null));
        later(Duration.ofMinutes(20));
        inTenant(() -> service.touch("s1", alice, null, null));
        inTenant(() -> service.touch("unknown", alice, "10.0.0.2", null));
        inTenant(() -> service.touch("unknown", alice, "10.0.0.2", null));
        later(Duration.ofMinutes(25));

        assertThat(inTenant(() -> service.listOwn(alice, null))).extracting(ActiveSession::clientIp)
                .containsExactlyInAnyOrder(null, "10.0.0.2");

        inTenant(() -> service.forget("unknown"));
        assertThat(inTenant(() -> service.listOwn(alice, null))).hasSize(1);
    }

    @Test
    void onlySystemAccountsListEverySession()
    {
        inTenant(() -> service.start("a1", admin, null, null));
        later(Duration.ofMinutes(1));
        inTenant(() -> service.start("s1", alice, null, null));

        PageResult<ActiveSession> page = inTenant(() -> service.listAll(admin, new PageQuery(1, 1), "a1"));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(ActiveSession::username).containsExactly("alice");
        assertThat(inTenant(() -> service.listAll(admin, new PageQuery(2, 1), "a1")).items())
                .extracting(ActiveSession::current).containsExactly(true);
        assertThatThrownBy(() -> inTenant(() -> service.listAll(alice, new PageQuery(1, 10), null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void usersEndOnlyTheirOwnSessions()
    {
        inTenant(() -> service.start("a1", admin, null, null));
        inTenant(() -> service.start("s1", alice, null, null));

        inTenant(() -> service.start("s2", alice, null, null));

        assertThatThrownBy(() -> inTenant(() -> service.revokeOwn(alice, idOf("a1"), "s2")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(inTenant(() -> service.revokeOwn(alice, idOf("s1"), "s2"))).isFalse();
        // The request's own session is left to the caller to invalidate, but forgotten all the same.
        assertThat(inTenant(() -> service.revokeOwn(alice, idOf("s2"), "s2"))).isTrue();

        assertThat(recorded().sessions()).containsExactly("s1");
        assertThat(inTenant(() -> sessions.findByAccountId(alice))).isEmpty();
    }

    @Test
    void administratorsEndAnySessionOfTheTenant()
    {
        inTenant(() -> service.start("s1", alice, null, null));
        long id = idOf("s1");

        assertThatThrownBy(() -> inTenant(() -> service.revoke(alice, id, null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThat(inTenant(() -> service.revoke(admin, id, null))).isFalse();
        assertThatThrownBy(() -> inTenant(() -> service.revoke(admin, id, null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));

        assertThat(recorded().sessions()).containsExactly("s1");
    }

    @Test
    void revokingAnAccountEndsAllItsSessions()
    {
        inTenant(() -> service.start("s1", alice, null, null));
        inTenant(() -> service.start("a1", admin, null, null));

        inTenant(() -> service.revokeAll(alice));

        assertThat(recorded().accounts()).containsExactly(alice);
        assertThat(inTenant(() -> sessions.findAll())).extracting(ConsoleSession::getSessionId).containsExactly("a1");
    }
}
