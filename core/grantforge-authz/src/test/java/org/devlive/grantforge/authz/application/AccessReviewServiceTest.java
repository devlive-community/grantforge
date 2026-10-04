// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.AccessReviewItemRepository;
import org.devlive.grantforge.authz.domain.AccessReviewRepository;
import org.devlive.grantforge.authz.domain.AccessReviewRoleRepository;
import org.devlive.grantforge.authz.domain.AccessReviewRoundRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.devlive.grantforge.authz.domain.ReviewOutcome;
import org.devlive.grantforge.authz.domain.ReviewRoundStatus;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SodConstraintRepository;
import org.devlive.grantforge.authz.domain.SodConstraintRoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.devlive.grantforge.authz.application.CatalogFixture.errorOf;

@DataJpaTest
@Import({AccessReviewService.class, SodService.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class,
        RoleService.class, SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class,
        AuthorizationVersions.class, RoleAssignmentService.class, RoleHolders.class, AccessRequestServiceTest.MovingClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccessReviewServiceTest
{
    private static final Instant START = AccessRequestServiceTest.START;

    @Autowired
    private AccessReviewService service;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleAssignmentService assignmentService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private AccessReviewRepository reviews;

    @Autowired
    private AccessReviewRoleRepository reviewRoles;

    @Autowired
    private AccessReviewRoundRepository rounds;

    @Autowired
    private AccessReviewItemRepository items;

    @Autowired
    private SodConstraintRepository constraints;

    @Autowired
    private SodConstraintRoleRepository constraintRoles;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private RoleParentRepository parents;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserGroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository unitMembers;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private AccountPositionRepository holdings;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private AuditEventRepository audit;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PlatformAdministrators platform;

    private CatalogFixture catalog;
    private AssignmentFixture people;
    private long reports;

    @BeforeEach
    void createRoles()
    {
        AccessRequestServiceTest.MovingClock.NOW.set(START);
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        provisioner.provision(catalog.tenant, false);
        reports = as(() -> roleService.create(catalog.boss, "reports", "Reports", null)).id();
        as(() -> assignmentService.assign(catalog.boss, reports, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED));
        as(() -> assignmentService.assign(catalog.boss, reports, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED));
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            items.deleteAllInBatch();
            rounds.deleteAllInBatch();
            reviewRoles.deleteAllInBatch();
            reviews.deleteAllInBatch();
            constraintRoles.deleteAllInBatch();
            constraints.deleteAllInBatch();
            parents.deleteAllInBatch();
            return null;
        });
        AssignmentFixture.deleteRows(assignments, roles, groupMembers, unitMembers, holdings, groups, units, positions);
        catalog.deleteRows(resources, applications, audit, transactionManager);
    }

    private <T> T as(Supplier<T> action)
    {
        return catalog.inTenant(action);
    }

    private static void at(Duration later)
    {
        AccessRequestServiceTest.MovingClock.NOW.set(START.plus(later));
    }

    private AccessReviewView review(Set<Long> roleIds, ReviewFallback fallback, @Nullable Integer interval, @Nullable Instant next)
    {
        return as(() -> service.create(catalog.boss, new AccessReviewCommand(" Quarterly ", "Reports access", roleIds, 7, interval, fallback, true, next)));
    }

    private List<ReviewItemView> itemsOf(long round, ReviewDecision... decisions)
    {
        return as(() -> service.items(round, List.of(decisions), PageQuery.of(1, 200))).items();
    }

    private ReviewItemView itemFor(long round, SubjectType type)
    {
        return itemsOf(round).stream().filter(item -> item.subject().type() == type).findFirst().orElseThrow();
    }

    private boolean holds(long account, long role)
    {
        return as(() -> assignmentService.rolesOf(catalog.boss, account)).stream().anyMatch(held -> held.role().id() == role && held.active());
    }

    @Test
    void reviewersKeepAndRevokeAssignmentsThatApplyWhenTheRoundCompletes()
    {
        AccessReviewView created = review(Set.of(reports), ReviewFallback.KEEP, null, null);
        assertThat(created).extracting(AccessReviewView::name, AccessReviewView::durationDays, AccessReviewView::openRound)
                .containsExactly("Quarterly", 7, null);

        ReviewRoundView round = as(() -> service.start(catalog.boss, created.id()));
        assertThat(round).extracting(ReviewRoundView::status, ReviewRoundView::dueAt).containsExactly(ReviewRoundStatus.OPEN, START.plus(Duration.ofDays(7)));
        assertThat(round.startedBy()).isNotNull().extracting(Subject::id).isEqualTo(catalog.boss);
        assertThat(round.progress()).isEqualTo(new ReviewProgress(2, 2, 0, 0, 0));
        assertThatThrownBy(() -> as(() -> service.start(catalog.boss, created.id())))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_RUNNING));

        long alice = itemFor(round.id(), SubjectType.USER).id();
        long dev = itemFor(round.id(), SubjectType.GROUP).id();
        assertThat(itemFor(round.id(), SubjectType.GROUP).subject().name()).isEqualTo("Developers");
        as(() -> service.decide(catalog.boss, round.id(), List.of(alice), ReviewDecision.REVOKE, null));
        // Decisions change until the round completes.
        List<ReviewItemView> decided = as(() -> service.decide(catalog.boss, round.id(), List.of(alice), ReviewDecision.KEEP, " still needed "));
        assertThat(decided).singleElement().extracting(ReviewItemView::decision, ReviewItemView::comment).containsExactly(ReviewDecision.KEEP, "still needed");
        as(() -> service.decide(catalog.boss, round.id(), List.of(dev), ReviewDecision.REVOKE, "team left the project"));
        assertThat(itemsOf(round.id(), ReviewDecision.REVOKE)).extracting(item -> item.subject().name()).containsExactly("Developers");
        assertThat(as(() -> service.list())).singleElement().extracting(AccessReviewView::openRound).isNotNull()
                .extracting(ReviewRoundView::progress).isEqualTo(new ReviewProgress(2, 0, 1, 1, 0));
        // Nothing is removed before the round completes.
        assertThat(as(() -> assignmentService.list(catalog.boss, reports))).hasSize(2);

        ReviewRoundView completed = as(() -> service.complete(catalog.boss, round.id()));

        assertThat(completed.status()).isEqualTo(ReviewRoundStatus.COMPLETED);
        assertThat(completed.progress().revoked()).isOne();
        assertThat(as(() -> assignmentService.list(catalog.boss, reports))).singleElement().extracting(view -> view.subject().id()).isEqualTo(people.alice);
        assertThat(itemsOf(round.id())).extracting(ReviewItemView::outcome).containsExactly(ReviewOutcome.KEPT, ReviewOutcome.REVOKED);
        assertThat(itemFor(round.id(), SubjectType.GROUP).assigned()).as("the assignment is gone").isFalse();
        assertThat(as(() -> service.list()).get(0).openRound()).isNull();
        assertThatThrownBy(() -> as(() -> service.decide(catalog.boss, round.id(), List.of(alice), ReviewDecision.REVOKE, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_CLOSED));
        assertThatThrownBy(() -> as(() -> service.complete(catalog.boss, round.id())))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_CLOSED));
        assertThat(audit.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.ACCESS_REVIEW_CREATED, AuditAction.ACCESS_REVIEW_STARTED,
                AuditAction.ACCESS_REVIEW_DECIDED, AuditAction.ACCESS_REVIEW_REVOKED, AuditAction.ACCESS_REVIEW_COMPLETED);
    }

    @Test
    void nobodyReviewsTheirOwnAccess()
    {
        long round = as(() -> service.start(catalog.boss, review(Set.of(reports), ReviewFallback.KEEP, null, null).id())).id();

        // Alice has the role herself and through her group.
        for (SubjectType type : List.of(SubjectType.USER, SubjectType.GROUP)) {
            long item = itemFor(round, type).id();
            assertThatThrownBy(() -> as(() -> service.decide(people.alice, round, List.of(item), ReviewDecision.KEEP, null)))
                    .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_SELF));
        }
        assertThat(as(() -> service.decide(catalog.member, round, List.of(itemFor(round, SubjectType.GROUP).id()), ReviewDecision.KEEP, null)))
                .hasSize(1);
    }

    @Test
    void roundsStartOnScheduleAndCompleteWhenDueWithTheFallback()
    {
        AccessReviewView scheduled = review(Set.of(reports), ReviewFallback.REVOKE, 30, START.plus(Duration.ofHours(1)));
        assertThat(service.runDue()).isZero();

        at(Duration.ofHours(1));
        assertThat(service.runDue()).isOne();
        assertThat(service.runDue()).as("one open round at a time").isZero();
        AccessReviewView running = as(() -> service.list()).get(0);
        assertThat(running.openRound()).isNotNull().extracting(ReviewRoundView::startedBy).isNull();
        assertThat(running.nextRunAt()).isEqualTo(START.plus(Duration.ofHours(1)).plus(Duration.ofDays(30)));
        long round = requireOpenRound(running);
        as(() -> service.decide(catalog.boss, round, List.of(itemFor(round, SubjectType.USER).id()), ReviewDecision.KEEP, null));

        // Due: the undecided group assignment follows the fallback and goes.
        at(Duration.ofDays(8));
        assertThat(service.runDue()).isOne();
        assertThat(holds(people.alice, reports)).isTrue();
        assertThat(as(() -> assignmentService.list(catalog.boss, reports))).singleElement().extracting(view -> view.subject().type())
                .isEqualTo(SubjectType.USER);
        assertThat(itemsOf(round)).extracting(ReviewItemView::decision, ReviewItemView::outcome).containsExactly(
                tuple(ReviewDecision.KEEP, ReviewOutcome.KEPT),
                tuple(ReviewDecision.PENDING, ReviewOutcome.REVOKED));

        at(Duration.ofDays(31));
        assertThat(service.runDue()).isOne();
        List<ReviewRoundView> history = as(() -> service.rounds(scheduled.id()));
        assertThat(history).extracting(ReviewRoundView::status).containsExactly(ReviewRoundStatus.OPEN, ReviewRoundStatus.COMPLETED);
        assertThat(history.get(0).progress().total()).isOne();
        assertThat(history.get(1).endedBy()).isNull();
    }

    private static long requireOpenRound(AccessReviewView view)
    {
        ReviewRoundView open = view.openRound();
        assertThat(open).isNotNull();
        return open == null ? -1 : open.id();
    }

    @Test
    void leavesSystemAccountsTheirSystemRoles()
    {
        long admin = as(() -> roles.findByCode(SystemRole.TENANT_ADMIN.code())).orElseThrow().requireId();
        as(() -> assignmentService.assign(catalog.boss, admin, SubjectType.USER, catalog.member, RoleAssignment.Terms.UNLIMITED));

        long round = as(() -> service.start(catalog.boss, review(Set.of(admin), ReviewFallback.REVOKE, null, null).id())).id();

        assertThat(itemsOf(round)).extracting(item -> item.subject().id()).containsExactly(catalog.member);
        as(() -> service.complete(catalog.boss, round));
        assertThat(holds(catalog.boss, admin)).isTrue();
        assertThat(holds(catalog.member, admin)).isFalse();
    }

    @Test
    void onlyPlatformAdministratorsRevokeThePlatformAdministratorRole()
    {
        provisioner.provision(catalog.platform, true);
        long rita = catalog.asRoot(() -> accounts.save(UserAccount.create("rita", "h", Instant.EPOCH)).requireId());
        long pat = catalog.asRoot(() -> accounts.save(UserAccount.create("pat", "h", Instant.EPOCH)).requireId());
        long admin = catalog.asRoot(() -> roles.findByCode(SystemRole.PLATFORM_ADMIN.code())).orElseThrow().requireId();
        catalog.asRoot(() -> assignments.save(RoleAssignment.create(admin, SubjectType.USER, pat, RoleAssignment.Terms.UNLIMITED)));
        long review = catalog.asRoot(() -> service.create(catalog.root, new AccessReviewCommand("Admins", null, Set.of(admin), 7, null,
                ReviewFallback.KEEP, false, null))).id();
        long round = catalog.asRoot(() -> service.start(catalog.root, review)).id();
        long item = catalog.asRoot(() -> service.items(round, List.of(), PageQuery.of(1, 10))).items().get(0).id();

        assertThatThrownBy(() -> catalog.asRoot(() -> service.decide(rita, round, List.of(item), ReviewDecision.REVOKE, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ROLE_NOT_ASSIGNABLE));
        assertThat(catalog.asRoot(() -> service.decide(rita, round, List.of(item), ReviewDecision.KEEP, null))).hasSize(1);
        assertThat(catalog.asRoot(() -> service.decide(catalog.root, round, List.of(item), ReviewDecision.REVOKE, null))).hasSize(1);
    }

    @Test
    void checksReviewsAndDecisions()
    {
        long admin = as(() -> roles.findByCode(SystemRole.TENANT_ADMIN.code())).orElseThrow().requireId();
        assertThatThrownBy(() -> review(Set.of(), ReviewFallback.KEEP, null, null))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> review(Set.of(404L), ReviewFallback.KEEP, null, null))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> review(Set.of(reports), ReviewFallback.KEEP, 6, null))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.create(catalog.boss, new AccessReviewCommand(" ", null, Set.of(reports), 7, null, ReviewFallback.KEEP,
                true, null)))).satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.create(catalog.boss, new AccessReviewCommand("R", "x".repeat(513), Set.of(reports), 7, null,
                ReviewFallback.KEEP, true, null)))).satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.create(catalog.boss, new AccessReviewCommand("R", null, Set.of(reports), 91, null,
                ReviewFallback.KEEP, true, null)))).satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.start(catalog.boss, 404L)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));

        AccessReviewView created = review(Set.of(reports), ReviewFallback.KEEP, null, null);
        AccessReviewView updated = as(() -> service.update(catalog.boss, created.id(), new AccessReviewCommand("Monthly", null, Set.of(reports, admin), 3,
                30, ReviewFallback.REVOKE, false, START)));
        assertThat(updated).extracting(AccessReviewView::name, AccessReviewView::durationDays, AccessReviewView::intervalDays,
                AccessReviewView::unreviewed, AccessReviewView::enabled, AccessReviewView::nextRunAt)
                .containsExactly("Monthly", 3, 30, ReviewFallback.REVOKE, false, START);
        assertThat(updated.roles()).extracting(RoleView::code).containsExactly("reports", SystemRole.TENANT_ADMIN.code());
        assertThat(service.runDue()).as("disabled").isZero();

        long round = as(() -> service.start(catalog.boss, created.id())).id();
        long item = itemsOf(round).get(0).id();
        assertThatThrownBy(() -> as(() -> service.decide(catalog.boss, round, List.of(item), ReviewDecision.KEEP, "x".repeat(501))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.decide(catalog.boss, round, List.of(), ReviewDecision.KEEP, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.decide(catalog.boss, round, LongStream.rangeClosed(1, 201).boxed().toList(), ReviewDecision.KEEP, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_INVALID));
        assertThatThrownBy(() -> as(() -> service.decide(catalog.boss, round, List.of(item, 404L), ReviewDecision.KEEP, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> as(() -> service.decide(catalog.boss, 404L, List.of(item), ReviewDecision.KEEP, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        PageResult<ReviewItemView> page = as(() -> service.items(round, List.of(), PageQuery.of(1, 1)));
        assertThat(page.items()).hasSize(1);
        // The tenant administrator role is under review too, but only the system account holds it.
        assertThat(page.total()).isEqualTo(2);

        // Cancelling applies nothing, even with the fallback to revoke.
        assertThat(as(() -> service.cancel(catalog.boss, round)).status()).isEqualTo(ReviewRoundStatus.CANCELLED);
        assertThat(as(() -> assignmentService.list(catalog.boss, reports))).hasSize(2);
        assertThatThrownBy(() -> as(() -> service.cancel(catalog.boss, round)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REVIEW_CLOSED));

        as(() -> {
            service.delete(catalog.boss, created.id());
            return null;
        });
        assertThat(as(() -> service.list())).isEmpty();
        assertThat(TenantContext.callAsSystem(() -> rounds.count() + items.count())).isZero();
        assertThatThrownBy(() -> as(() -> service.rounds(created.id())))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> as(() -> service.items(round, List.of(), PageQuery.of(1, 10))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
