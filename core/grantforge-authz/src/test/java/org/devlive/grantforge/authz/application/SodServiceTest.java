// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({SodService.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, RoleService.class,
        SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class, AuthorizationVersions.class,
        RoleAssignmentService.class, RoleHolders.class, RoleInheritanceService.class, SodServiceTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SodServiceTest
{
    static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    /** Pins the current time. */
    static class FixedClock
    {
        @Bean
        @Primary
        Clock fixedClock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private SodService sod;

    @Autowired
    private RoleAssignmentService assignmentService;

    @Autowired
    private RoleInheritanceService inheritance;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SystemRoleProvisioner provisioner;

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
    private long payer;
    private long approver;
    private long auditor;

    @BeforeEach
    void createRoles()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        provisioner.provision(catalog.tenant, false);
        payer = as(() -> roleService.create(catalog.boss, "payer", "Payer", null)).id();
        approver = as(() -> roleService.create(catalog.boss, "approver", "Approver", null)).id();
        auditor = as(() -> roleService.create(catalog.boss, "auditor", "Auditor", null)).id();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
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

    private SodConstraintView constraint(String code, SodMode mode, long... roleIds)
    {
        Set<Long> ids = new HashSet<>();
        for (long id : roleIds) {
            ids.add(id);
        }
        return as(() -> sod.create(catalog.boss, new SodConstraintCommand(code, "Constraint " + code, null, ids, 1, mode, true)));
    }

    private AssignmentView assign(long role, SubjectType type, long subject, RoleAssignment.Terms terms)
    {
        return as(() -> assignmentService.assign(catalog.boss, role, type, subject, terms));
    }

    private static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void refusesAssignmentsThatGiveSomeoneRolesKeptApart()
    {
        constraint("payments", SodMode.ENFORCE, payer, approver);
        assign(payer, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);

        // Alice is in the group, so the group's role would reach her.
        assertThatThrownBy(() -> assign(approver, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED))
                .satisfies(error -> {
                    assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.SOD_CONFLICT);
                    assertThat(((GrantForgeException) error).getArguments()).containsExactly("Alice A", "Approver, Payer", "Constraint payments");
                });
        assertThat(as(() -> assignmentService.list(catalog.boss, approver))).isEmpty();
        // Someone else may have the role.
        assign(approver, SubjectType.USER, catalog.boss, RoleAssignment.Terms.UNLIMITED);

        // A lapsed assignment gives nothing, until its validity changes.
        AssignmentView lapsed = assign(approver, SubjectType.POSITION, people.cfo, new RoleAssignment.Terms(null, NOW.minusSeconds(1), false));
        assertThatThrownBy(() -> as(() -> assignmentService.change(catalog.boss, lapsed.id(), RoleAssignment.Terms.UNLIMITED)))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.SOD_CONFLICT));
    }

    @Test
    void refusesInheritanceThatGivesHoldersRolesKeptApart()
    {
        constraint("payments", SodMode.ENFORCE, payer, approver);
        long chief = as(() -> roleService.create(catalog.boss, "chief", "Chief", null)).id();
        assign(payer, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assign(chief, SubjectType.ORG_UNIT, people.sales, RoleAssignment.Terms.UNLIMITED);

        // Alice holds Chief through her department; inheriting Approver would give her both roles.
        assertThatThrownBy(() -> as(() -> inheritance.setParents(catalog.boss, chief, List.of(approver))))
                .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.SOD_CONFLICT));
        assertThat(as(() -> parents.findByRoleId(chief))).isEmpty();
        as(() -> inheritance.setParents(catalog.boss, chief, List.of(auditor)));
    }

    @Test
    void reportsConflictsAndLetsReportOnlyConstraintsThrough()
    {
        SodConstraintView audits = constraint("audits", SodMode.REPORT, payer, auditor);
        assign(payer, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assign(auditor, SubjectType.POSITION, people.cfo, RoleAssignment.Terms.UNLIMITED);

        List<SodConflict> conflicts = as(() -> sod.conflicts());

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).constraint().id()).isEqualTo(audits.id());
        assertThat(conflicts.get(0).account()).isEqualTo(new Subject(SubjectType.USER, people.alice, "Alice A", "alice"));
        assertThat(conflicts.get(0).roles()).extracting(RoleView::code).containsExactly("auditor", "payer");

        // A conflict that exists already does not block unrelated changes once the constraint is enforced.
        as(() -> sod.update(catalog.boss, audits.id(), new SodConstraintCommand("ignored", "Audits", "Auditors do not pay", Set.of(payer, auditor),
                1, SodMode.ENFORCE, true)));
        long readers = as(() -> roleService.create(catalog.boss, "readers", "Readers", null)).id();
        assign(readers, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assertThat(as(() -> sod.conflicts())).hasSize(1);
        // A disabled constraint reports nothing.
        as(() -> sod.update(catalog.boss, audits.id(), new SodConstraintCommand("audits", "Audits", null, Set.of(payer, auditor), 1, SodMode.ENFORCE,
                false)));
        assertThat(as(() -> sod.conflicts())).isEmpty();
    }

    @Test
    void countsRolesHeldThroughInheritance()
    {
        constraint("payments", SodMode.REPORT, payer, approver);
        long chief = as(() -> roleService.create(catalog.boss, "chief", "Chief", null)).id();
        as(() -> inheritance.setParents(catalog.boss, chief, List.of(approver)));
        assign(payer, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assign(chief, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED);

        assertThat(as(() -> sod.conflicts())).singleElement().extracting(conflict -> conflict.roles().size()).isEqualTo(2);
        assertThat(as(() -> sod.list())).isNotEmpty();
    }

    @Test
    void keepsConstraintsWithTheirRoles()
    {
        SodConstraintView created = constraint("payments", SodMode.ENFORCE, payer, approver);
        assertThat(created.roles()).extracting(RoleView::code).containsExactly("approver", "payer");
        assertThat(created.maxRoles()).isOne();

        SodConstraintView three = as(() -> sod.update(catalog.boss, created.id(), new SodConstraintCommand("payments", "Payments", "Two of three",
                Set.of(payer, approver, auditor), 2, SodMode.REPORT, true)));
        assertThat(three).extracting(SodConstraintView::name, SodConstraintView::description, SodConstraintView::maxRoles, SodConstraintView::mode)
                .containsExactly("Payments", "Two of three", 2, SodMode.REPORT);
        assertThat(three.roles()).hasSize(3);
        assertThat(as(() -> sod.list())).extracting(SodConstraintView::code).containsExactly("payments");

        as(() -> {
            sod.delete(catalog.boss, created.id());
            return null;
        });
        assertThat(as(() -> sod.list())).isEmpty();
        assertThat(audit.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.SOD_CONSTRAINT_CREATED, AuditAction.SOD_CONSTRAINT_UPDATED,
                AuditAction.SOD_CONSTRAINT_DELETED);
    }

    @Test
    void refusesConstraintsThatCannotWork()
    {
        constraint("payments", SodMode.ENFORCE, payer, approver);
        for (SodConstraintCommand invalid : new SodConstraintCommand[] {
            new SodConstraintCommand("Bad Code", "x", null, Set.of(payer, approver), 1, SodMode.ENFORCE, true),
            new SodConstraintCommand("one", "x", null, Set.of(payer), 1, SodMode.ENFORCE, true),
            new SodConstraintCommand("all", "x", null, Set.of(payer, approver), 2, SodMode.ENFORCE, true),
            new SodConstraintCommand("none", "x", null, Set.of(payer, approver), 0, SodMode.ENFORCE, true),
            new SodConstraintCommand("nameless", " ", null, Set.of(payer, approver), 1, SodMode.ENFORCE, true),
            new SodConstraintCommand("wordy", "x", "d".repeat(513), Set.of(payer, approver), 1, SodMode.ENFORCE, true),
        }) {
            assertThatThrownBy(() -> as(() -> sod.create(catalog.boss, invalid)))
                    .satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.SOD_CONSTRAINT_INVALID));
        }
        assertThatThrownBy(() -> as(() -> sod.create(catalog.boss, new SodConstraintCommand("payments", "x", null, Set.of(payer, approver), 1,
                SodMode.ENFORCE, true)))).satisfies(error -> assertThat(errorOf(error)).isEqualTo(AuthzErrorCode.SOD_CODE_TAKEN));
        assertThatThrownBy(() -> as(() -> sod.create(catalog.boss, new SodConstraintCommand("ghost", "x", null, Set.of(payer, 404L), 1,
                SodMode.ENFORCE, true)))).satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> as(() -> sod.update(catalog.boss, 404L, new SodConstraintCommand("x", "x", null, Set.of(payer, approver), 1,
                SodMode.ENFORCE, true)))).satisfies(error -> assertThat(errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
