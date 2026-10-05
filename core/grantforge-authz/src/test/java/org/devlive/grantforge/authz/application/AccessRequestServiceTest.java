// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.AccessRequestRepository;
import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.RequestableRoleRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SodConstraintRepository;
import org.devlive.grantforge.authz.domain.SodConstraintRoleRepository;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AccessRequestService.class, SodService.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class,
        RoleService.class, SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class,
        AuthorizationVersions.class, RoleAssignmentService.class, RoleHolders.class, AccessRequestServiceTest.MovingClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccessRequestServiceTest
{
    static final Instant START = Instant.parse("2026-06-15T12:00:00Z");

    /** A clock the tests move forward. */
    static class MovingClock
    {
        static final AtomicReference<Instant> NOW = new AtomicReference<>(START);

        @Bean
        @Primary
        Clock movingClock()
        {
            return new Clock()
            {
                @Override
                public ZoneId getZone()
                {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone)
                {
                    return this;
                }

                @Override
                public Instant instant()
                {
                    return requireNonNull(NOW.get());
                }
            };
        }
    }

    @Autowired
    private AccessRequestService service;

    @Autowired
    private SodService sod;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleAssignmentService assignmentService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private AccessRequestRepository requests;

    @Autowired
    private RequestableRoleRepository requestable;

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
    private long payer;

    @BeforeEach
    void createRoles()
    {
        MovingClock.NOW.set(START);
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        provisioner.provision(catalog.tenant, false);
        reports = as(() -> roleService.create(catalog.boss, "reports", "Reports", null)).id();
        payer = as(() -> roleService.create(catalog.boss, "payer", "Payer", null)).id();
        as(() -> service.setRequestable(catalog.boss, Map.of(reports, 30, payer, 7)));
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            requests.deleteAllInBatch();
            requestable.deleteAllInBatch();
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

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    private boolean aliceHolds(long role)
    {
        return as(() -> assignmentService.rolesOf(catalog.boss, people.alice)).stream().anyMatch(held -> held.role().id() == role && held.active());
    }

    @Test
    void grantsARoleForTheApprovedPeriodAndTakesItBackAfterwards()
    {
        AccessRequestView filed = as(() -> service.request(people.alice, reports, " Quarterly closing ", 10));
        assertThat(filed).extracting(AccessRequestView::status, AccessRequestView::reason, AccessRequestView::requestedDays)
                .containsExactly(AccessRequestStatus.PENDING, "Quarterly closing", 10);
        assertThat(as(() -> service.optionsFor(people.alice))).extracting(option -> option.role().code() + ":" + option.held() + ":" + option.pending())
                .containsExactly("payer:false:false", "reports:false:true");
        assertThat(as(() -> service.list(Set.of(AccessRequestStatus.PENDING)))).extracting(AccessRequestView::id).containsExactly(filed.id());

        // Nobody decides their own request.
        assertThatThrownBy(() -> as(() -> service.approve(people.alice, filed.id(), null, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_SELF_APPROVAL));
        assertThatThrownBy(() -> as(() -> service.approve(catalog.boss, filed.id(), 11, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_INVALID));

        AccessRequestView approved = as(() -> service.approve(catalog.boss, filed.id(), 5, "For the closing only"));

        assertThat(approved.status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(approved.validUntil()).isEqualTo(START.plus(Duration.ofDays(5)));
        assertThat(approved.decidedBy()).isNotNull().extracting(Subject::id).isEqualTo(catalog.boss);
        assertThat(approved.comment()).isEqualTo("For the closing only");
        assertThat(aliceHolds(reports)).isTrue();
        assertThat(as(() -> service.optionsFor(people.alice))).extracting(RequestOption::held).containsExactly(false, true);
        assertThatThrownBy(() -> as(() -> service.request(people.alice, reports, "again", 3)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_ALREADY_HELD));
        assertThatThrownBy(() -> as(() -> service.reject(catalog.boss, filed.id(), null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_DECIDED));

        // The grant stops at its end by itself, and the role is taken back.
        assertThat(service.expire()).isZero();
        MovingClock.NOW.set(START.plus(Duration.ofDays(5)));
        assertThat(aliceHolds(reports)).isFalse();
        assertThat(service.expire()).isOne();
        assertThat(as(() -> service.mine(people.alice))).singleElement().extracting(AccessRequestView::status).isEqualTo(AccessRequestStatus.EXPIRED);
        assertThat(as(() -> assignmentService.list(catalog.boss, reports))).isEmpty();
        assertThat(audit.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.ACCESS_REQUESTED, AuditAction.ACCESS_REQUEST_APPROVED,
                AuditAction.ROLE_ASSIGNED, AuditAction.ACCESS_GRANT_EXPIRED);
    }

    @Test
    void rejectsCancelsAndRevokes()
    {
        AccessRequestView first = as(() -> service.request(people.alice, reports, "Look at reports", 30));
        assertThatThrownBy(() -> as(() -> service.request(people.alice, reports, "Twice", 3)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_PENDING));
        assertThatThrownBy(() -> as(() -> service.cancel(catalog.boss, first.id())))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(as(() -> service.cancel(people.alice, first.id())).status()).isEqualTo(AccessRequestStatus.CANCELLED);

        AccessRequestView second = as(() -> service.request(people.alice, reports, "Look at reports", 30));
        assertThat(as(() -> service.reject(catalog.boss, second.id(), "Ask your manager")).status()).isEqualTo(AccessRequestStatus.REJECTED);

        AccessRequestView third = as(() -> service.request(people.alice, payer, "Pay invoices", 7));
        as(() -> service.approve(catalog.boss, third.id(), null, null));
        assertThatThrownBy(() -> as(() -> service.revoke(catalog.boss, second.id())))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_DECIDED));
        AccessRequestView revoked = as(() -> service.revoke(catalog.boss, third.id()));

        assertThat(revoked.status()).isEqualTo(AccessRequestStatus.REVOKED);
        assertThat(revoked.endedAt()).isEqualTo(START);
        assertThat(aliceHolds(payer)).isFalse();
        assertThat(as(() -> service.mine(people.alice))).extracting(AccessRequestView::status)
                .containsExactlyInAnyOrder(AccessRequestStatus.CANCELLED, AccessRequestStatus.REJECTED, AccessRequestStatus.REVOKED);
        assertThat(as(() -> service.list(List.of()))).hasSize(3);
    }

    @Test
    void approvingFollowsTheAssignmentRules()
    {
        long approver = as(() -> roleService.create(catalog.boss, "approver", "Approver", null)).id();
        as(() -> service.setRequestable(catalog.boss, Map.of(payer, 7, approver, 7)));
        as(() -> sod.create(catalog.boss, new SodConstraintCommand("payments", "Payments", null, Set.of(payer, approver), 1, SodMode.ENFORCE, true)));
        as(() -> assignmentService.assign(catalog.boss, payer, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED));

        AccessRequestView filed = as(() -> service.request(people.alice, approver, "Approve this month", 7));

        assertThatThrownBy(() -> as(() -> service.approve(catalog.boss, filed.id(), null, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.SOD_CONFLICT));
        assertThat(as(() -> service.mine(people.alice))).singleElement().extracting(AccessRequestView::status).isEqualTo(AccessRequestStatus.PENDING);
        // Roles no longer listed cannot be asked for.
        assertThatThrownBy(() -> as(() -> service.request(people.alice, reports, "Reports", 3)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_NOT_REQUESTABLE));
    }

    @Test
    void checksRequestsAndSettings()
    {
        assertThatThrownBy(() -> as(() -> service.request(people.alice, reports, " ", 3)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_INVALID));
        assertThatThrownBy(() -> as(() -> service.request(people.alice, payer, "x", 8)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_INVALID));
        assertThatThrownBy(() -> as(() -> service.approve(catalog.boss, 404L, null, "x".repeat(501))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_INVALID));
        assertThatThrownBy(() -> as(() -> service.approve(catalog.boss, 404L, null, null)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        long admin = as(() -> roles.findByCode("tenant-admin")).orElseThrow().requireId();
        assertThatThrownBy(() -> as(() -> service.setRequestable(catalog.boss, Map.of(admin, 7))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_INVALID));
        assertThatThrownBy(() -> as(() -> service.setRequestable(catalog.boss, Map.of(payer, 366))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_REQUEST_INVALID));
        assertThatThrownBy(() -> as(() -> service.setRequestable(catalog.boss, Map.of(404L, 7))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(as(() -> service.requestableRoles())).extracting(view -> view.role().code() + ":" + view.maxDays())
                .containsExactly("payer:7", "reports:30");
        // A disabled role is neither offered nor granted.
        as(() -> roleService.enable(catalog.boss, payer, false));
        assertThat(as(() -> service.optionsFor(people.alice))).extracting(option -> option.role().code()).containsExactly("reports");
        assertThatThrownBy(() -> as(() -> service.request(people.alice, payer, "x", 3)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.ACCESS_NOT_REQUESTABLE));
    }
}
