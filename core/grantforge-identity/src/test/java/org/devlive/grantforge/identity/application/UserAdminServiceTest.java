// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.ConsoleSessionRepository;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserState;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.FieldErrorCode;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@RecordApplicationEvents
@Import({AuditLog.class, IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class,
        ConsoleSessionService.class, UserAdminService.class, TestRowScopes.class, TestFieldRules.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserAdminServiceTest
{
    @Autowired
    private ApplicationEvents published;

    private static final String PASSWORD = "a long enough password";

    @Autowired
    private UserAdminService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private AccountPositionRepository holdings;

    @Autowired
    private ConsoleSessionRepository sessions;

    @Autowired
    private ConsoleSessionService consoleSessions;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private SessionTerminator terminator;

    @Autowired
    private TestRowScopes scopes;

    @Autowired
    private TestFieldRules fieldRules;

    private long tenant;
    private long admin;
    private long other;
    private long hq;
    private long lab;

    @BeforeEach
    void createTenant()
    {
        ((RecordingSessionTerminator) terminator).clear();
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", Instant.EPOCH).markSystemAccount()).requireId());
        other = inTenant(() -> accounts.save(UserAccount.create("root2", "h", Instant.EPOCH).markSystemAccount()).requireId());
        hq = inTenant(() -> units.save(OrgUnit.create(null, "hq", "总部", 0)).requireId());
        lab = inTenant(() -> units.save(OrgUnit.create(null, "lab", "实验室", 1)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        scopes.clear();
        fieldRules.clear();
        TenantContext.callAsSystem(() -> {
            sessions.deleteAllInBatch();
            members.deleteAllInBatch();
            holdings.deleteAllInBatch();
            positions.deleteAllInBatch();
            accounts.deleteAllInBatch();
            units.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action);
    }

    private static ErrorCode codeOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    private UserDetail createAlice()
    {
        return inTenant(() -> service.create(admin, "Alice", PASSWORD,
                new UserProfileInput("Alice A", "alice@acme.io", hq, List.of(lab, hq), List.of())));
    }

    @Test
    void createsAccountsInDepartmentsWhoMustChangeTheirPassword()
    {
        UserDetail alice = createAlice();

        assertThat(alice.summary()).extracting(UserSummary::username, UserSummary::displayName, UserSummary::email,
                UserSummary::status, UserSummary::mustChangePassword, UserSummary::primaryUnitName)
                .containsExactly("Alice", "Alice A", "alice@acme.io", AccountStatus.ACTIVE, true, "总部");
        assertThat(alice.memberships()).extracting(UserMembership::unitName, UserMembership::primary)
                .containsExactly(tuple("总部", true), tuple("实验室", false));
        UserAccount stored = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("alice")).orElseThrow();
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(inTenant(() -> service.find(admin, alice.summary().id())).memberships()).hasSize(2);
    }

    @Test
    void assignsAndReplacesPositions()
    {
        long cfo = inTenant(() -> positions.save(Position.create("cfo", "财务总监", null, 2)).requireId());
        long dev = inTenant(() -> positions.save(Position.create("dev", "Developer", null, 1)).requireId());
        long alice = createAlice().summary().id();

        UserDetail held = inTenant(() -> service.update(admin, alice, new UserProfileInput(null, null, null, List.of(),
                List.of(cfo, dev, cfo))));
        assertThat(held.positions()).extracting(UserPosition::name).containsExactly("Developer", "财务总监");
        assertThat(inTenant(() -> service.update(admin, alice, new UserProfileInput(null, null, null, List.of(),
                List.of(dev)))).positions()).extracting(UserPosition::positionId).containsExactly(dev);
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, alice, new UserProfileInput(null, null, null,
                List.of(), List.of(-1L))))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void rejectsTakenNamesBadInputAndNonAdministrators()
    {
        long alice = createAlice().summary().id();

        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "ALICE", PASSWORD, new UserProfileInput(null, null,
                null, List.of(), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.USERNAME_TAKEN));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "a b", PASSWORD, new UserProfileInput(null, null,
                null, List.of(), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "bob", "short", new UserProfileInput(null, null,
                null, List.of(), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "bob", PASSWORD, new UserProfileInput(null, null,
                null, List.of(hq), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "bob", PASSWORD, new UserProfileInput(null, null,
                -1L, List.of(), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("bob"))).isEmpty();
    }

    @Test
    void searchesAndUpdatesAccounts()
    {
        long alice = createAlice().summary().id();

        assertThat(inTenant(() -> service.search(admin, new UserFilter(" ALI ", UserState.ACTIVE, hq, true),
                new PageQuery(1, 10))).items()).extracting(UserSummary::id).containsExactly(alice);
        assertThat(inTenant(() -> service.search(admin, new UserFilter("50%", null, null, false), new PageQuery(1, 10)))
                .total()).isZero();
        assertThat(inTenant(() -> service.search(admin, new UserFilter(null, null, lab, false), new PageQuery(1, 10)))
                .total()).isOne();
        assertThatThrownBy(() -> inTenant(() -> service.search(admin, new UserFilter(null, null, -1L, false),
                new PageQuery(1, 10)))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));

        UserDetail moved = inTenant(() -> service.update(admin, alice, new UserProfileInput(" ", "", lab, List.of(), List.of())));
        assertThat(moved.summary()).extracting(UserSummary::displayName, UserSummary::email, UserSummary::primaryUnitName)
                .containsExactly(null, null, "实验室");
        assertThat(moved.memberships()).hasSize(1);
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, alice, new UserProfileInput(null, "bad", null, List.of(), List.of()))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void disablingLockingAndResettingEndTheSessions()
    {
        long alice = createAlice().summary().id();
        inTenant(() -> {
            consoleSessions.start("alice-1", alice, null, null);
            return null;
        });

        assertThat(inTenant(() -> service.disable(admin, alice)).summary().status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(inTenant(() -> service.enable(admin, alice)).summary().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(inTenant(() -> service.lock(admin, alice)).summary().lockedUntil()).isEqualTo(UserAccount.LOCKED_INDEFINITELY);
        assertThat(inTenant(() -> service.unlock(admin, alice)).summary().lockedUntil()).isNull();
        assertThat(inTenant(() -> service.resetPassword(admin, alice, "a brand new password")).summary().mustChangePassword())
                .isTrue();

        assertThat(((RecordingSessionTerminator) terminator).accounts()).containsExactly(alice, alice, alice);
        assertThat(inTenant(() -> sessions.findByAccountId(alice))).isEmpty();
        UserAccount stored = inTenant(() -> accounts.findById(alice)).orElseThrow();
        assertThat(encoder.matches("a brand new password", stored.getPasswordHash())).isTrue();
        assertThat(events.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.USER_CREATED,
                AuditAction.USER_DISABLED, AuditAction.USER_ENABLED, AuditAction.USER_LOCKED, AuditAction.USER_UNLOCKED,
                AuditAction.USER_PASSWORD_RESET);
    }

    @Test
    void protectsSystemAccountsAndTheAdministratorsOwnAccount()
    {
        for (long protectedId : new long[] {admin, other}) {
            assertThatThrownBy(() -> inTenant(() -> service.disable(admin, protectedId)))
                    .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_PROTECTED));
            assertThatThrownBy(() -> inTenant(() -> service.lock(admin, protectedId)))
                    .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_PROTECTED));
            assertThatThrownBy(() -> inTenant(() -> {
                service.delete(admin, protectedId);
                return null;
            })).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_PROTECTED));
        }
        assertThatThrownBy(() -> inTenant(() -> service.resetPassword(admin, admin, PASSWORD)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ACCOUNT_PROTECTED));
        // Another administrator may still get a new password, for example after forgetting it.
        assertThat(inTenant(() -> service.resetPassword(admin, other, "another long password")).summary().systemAccount())
                .isTrue();
    }

    @Test
    void deletesAccountsWithTheirSessionsAndDepartments()
    {
        long alice = createAlice().summary().id();
        inTenant(() -> {
            consoleSessions.start("alice-1", alice, null, null);
            service.delete(admin, alice);
            return null;
        });

        assertThat(inTenant(() -> accounts.findById(alice))).isEmpty();
        assertThat(published.stream(IdentityDeleted.class)).containsExactly(new IdentityDeleted(IdentityDeleted.Kind.ACCOUNT, alice));
        assertThat(inTenant(() -> members.findByAccount(alice))).isEmpty();
        assertThat(((RecordingSessionTerminator) terminator).accounts()).containsExactly(alice);
        assertThatThrownBy(() -> inTenant(() -> service.find(admin, alice)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void actorsOnlyUseTheAccountsDepartmentsAndPositionsTheirDataScopeCovers()
    {
        long alice = createAlice().summary().id();
        long bob = inTenant(() -> service.create(admin, "bob", PASSWORD, new UserProfileInput(null, null, null, List.of(),
                List.of()))).summary().id();
        long ops = inTenant(() -> units.save(OrgUnit.create(null, "ops", "运维部", 2)).requireId());
        scopes.limit(UserAccount.class, DataAction.READ, TestRowScopes.where("usernameNorm", "alice"));
        scopes.limit(UserAccount.class, DataAction.UPDATE, TestRowScopes.where("usernameNorm", "alice"));
        scopes.limit(UserAccount.class, DataAction.DELETE, TestRowScopes.none());
        scopes.limit(OrgUnit.class, DataAction.READ, TestRowScopes.where("code", "hq"));

        assertThat(inTenant(() -> service.search(admin, UserFilter.ALL, new PageQuery(1, 10))))
                .satisfies(page -> assertThat(page.items()).extracting(UserSummary::username).containsExactly("Alice"))
                .satisfies(page -> assertThat(page.total()).isOne());
        assertThatThrownBy(() -> inTenant(() -> service.search(admin, new UserFilter(null, null, lab, false), new PageQuery(1, 10))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inTenant(() -> service.find(admin, bob)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inTenant(() -> service.disable(admin, bob)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        // Alice keeps the department the actor cannot see, but cannot be put in another one.
        assertThat(inTenant(() -> service.update(admin, alice, new UserProfileInput("Alice", null, hq, List.of(lab), List.of())))
                .memberships()).extracting(UserMembership::unitId).containsExactlyInAnyOrder(hq, lab);
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, alice, new UserProfileInput("Alice", null, hq,
                List.of(ops), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> {
            inTenant(() -> {
                service.delete(admin, alice);
                return null;
            });
        }).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void searchesEMailAddressesOnlyWhenTheActorSeesThemInFull()
    {
        createAlice();
        UserFilter byMail = new UserFilter("acme.io", null, null, false);
        assertThat(inTenant(() -> service.search(admin, byMail, new PageQuery(1, 10))).total()).isOne();
        fieldRules.see("user", "email", FieldView.masked(MaskStrategy.EMAIL));
        assertThat(inTenant(() -> service.search(admin, byMail, new PageQuery(1, 10))).total()).isZero();
        assertThat(inTenant(() -> service.search(admin, new UserFilter("alice", null, null, false), new PageQuery(1, 10))).total()).isOne();
    }

    @Test
    void keepsTheEMailAddressOfActorsWhoDoNotSeeItInFull()
    {
        long alice = createAlice().summary().id();
        fieldRules.see("user", "email", FieldView.masked(MaskStrategy.EMAIL));
        assertThat(inTenant(() -> service.update(admin, alice, new UserProfileInput("Alice B", "a***@acme.io", hq, List.of(), List.of())))
                .summary()).extracting(UserSummary::displayName, UserSummary::email).containsExactly("Alice B", "alice@acme.io");
        fieldRules.clear();
        assertThat(inTenant(() -> service.update(admin, alice, new UserProfileInput("Alice B", "alice@lab.io", hq, List.of(), List.of())))
                .summary().email()).isEqualTo("alice@lab.io");
    }

    @Test
    void refusesToSetReadOnlyFields()
    {
        long alice = createAlice().summary().id();
        fieldRules.readOnly("user", "email");
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, alice, new UserProfileInput("Alice", "alice@lab.io", hq, List.of(),
                List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(FieldErrorCode.READONLY_CHANGED));
        assertThat(inTenant(() -> service.update(admin, alice, new UserProfileInput("Alice B", "alice@acme.io", hq, List.of(), List.of())))
                .summary().displayName()).isEqualTo("Alice B");
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "bob", PASSWORD, new UserProfileInput(null, "bob@acme.io", null,
                List.of(), List.of())))).satisfies(error -> assertThat(codeOf(error)).isEqualTo(FieldErrorCode.READONLY_CHANGED));
        assertThat(inTenant(() -> service.create(admin, "bob", PASSWORD, new UserProfileInput(null, null, null, List.of(), List.of())))
                .summary().email()).isNull();
    }
}
