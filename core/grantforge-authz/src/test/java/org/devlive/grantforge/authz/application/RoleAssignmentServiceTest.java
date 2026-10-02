// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.IdentityDeleted;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({EffectiveRoles.class, AuthorizationEvaluator.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, RoleService.class, SystemRoleProvisioner.class,
        SubjectDirectory.class, RoleAssignmentService.class, AssignmentCleaner.class, RoleAssignmentServiceTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleAssignmentServiceTest
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
    private RoleAssignmentService service;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private RoleAssignmentRepository assignments;

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
    private long auditors;

    @BeforeEach
    void createPeople()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        provisioner.provision(catalog.tenant, false);
        auditors = asBoss(() -> roleService.create(catalog.boss, "auditors", "Auditors", null)).id();
    }

    @AfterEach
    void deleteRows()
    {
        AssignmentFixture.deleteRows(assignments, roles, groupMembers, unitMembers, holdings, groups, units, positions);
        catalog.deleteRows(resources, applications, audit, transactionManager);
    }

    private <T> T asBoss(Supplier<T> action)
    {
        return catalog.inTenant(action);
    }

    private AssignmentView assign(long role, SubjectType type, long subject, RoleAssignment.Terms terms)
    {
        return asBoss(() -> service.assign(catalog.boss, role, type, subject, terms));
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    private long role(String code)
    {
        return asBoss(() -> roles.findByCode(code)).orElseThrow().requireId();
    }

    @Test
    void accountsHaveRolesDirectlyAndThroughGroupsDepartmentsAndPositions()
    {
        long readers = asBoss(() -> roleService.create(catalog.boss, "readers", "Readers", null)).id();
        long buyers = asBoss(() -> roleService.create(catalog.boss, "buyers", "Buyers", null)).id();
        long expired = asBoss(() -> roleService.create(catalog.boss, "expired", "Expired", null)).id();
        long whole = asBoss(() -> roleService.create(catalog.boss, "whole", "Whole company", null)).id();
        AssignmentView direct = assign(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assign(auditors, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED);
        assign(readers, SubjectType.ORG_UNIT, people.sales, RoleAssignment.Terms.UNLIMITED);
        assign(buyers, SubjectType.POSITION, people.cfo, new RoleAssignment.Terms(null, NOW.plusSeconds(3600), false));
        assign(expired, SubjectType.USER, people.alice, new RoleAssignment.Terms(null, NOW.minusSeconds(1), false));
        // A parent department's role reaches members of sub-departments only when the assignment includes them.
        assign(whole, SubjectType.ORG_UNIT, people.hq, new RoleAssignment.Terms(null, null, true));
        long only = asBoss(() -> roleService.create(catalog.boss, "only-hq", "HQ only", null)).id();
        assign(only, SubjectType.ORG_UNIT, people.hq, RoleAssignment.Terms.UNLIMITED);

        assertThat(direct.subject()).isEqualTo(new Subject(SubjectType.USER, people.alice, "Alice A", "alice"));
        assertThat(direct.valid()).isTrue();
        List<EffectiveRole> effective = asBoss(() -> service.rolesOf(catalog.boss, people.alice));
        assertThat(effective).extracting(role -> role.role().code() + ":" + role.active() + ":" + role.sources().size())
                .containsExactly("auditors:true:2", "buyers:true:1", "readers:true:1", "whole:true:1", "expired:false:1");

        asBoss(() -> roleService.enable(catalog.boss, auditors, false));
        assertThat(asBoss(() -> service.rolesOf(catalog.boss, people.alice))).filteredOn(role -> "auditors".equals(role.role().code()))
                .singleElement().satisfies(role -> assertThat(role.active()).isFalse());
        assertThat(asBoss(() -> service.list(catalog.boss, auditors))).extracting(view -> view.subject().type())
                .containsExactly(SubjectType.USER, SubjectType.GROUP);
    }

    @Test
    void assignmentsChangeAreRemovedAndEveryChangeIsAudited()
    {
        AssignmentView unit = assign(auditors, SubjectType.ORG_UNIT, people.hq, RoleAssignment.Terms.UNLIMITED);
        AssignmentView changed = asBoss(() -> service.change(catalog.boss, unit.id(), new RoleAssignment.Terms(NOW.plusSeconds(60),
                null, true)));

        assertThat(changed.terms().includeSubUnits()).isTrue();
        assertThat(changed.valid()).isFalse();
        asBoss(() -> {
            service.remove(catalog.boss, unit.id());
            return null;
        });
        assertThat(assignments.existsById(unit.id())).isFalse();
        assertThat(CatalogFixture.trail(audit)).filteredOn(line -> line.startsWith("ROLE_ASSIGN") || line.startsWith("ROLE_UNASSIGNED"))
                .containsExactly("ROLE_ASSIGNED:" + catalog.boss + ":ORG_UNIT:" + people.hq,
                        "ROLE_ASSIGNMENT_CHANGED:" + catalog.boss + ":ORG_UNIT:" + people.hq,
                        "ROLE_UNASSIGNED:" + catalog.boss + ":ORG_UNIT:" + people.hq);
    }

    @Test
    void refusesDuplicatesBadPeriodsUnknownSubjectsAndProtectsSystemAccounts()
    {
        assign(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        long tenantAdmin = role(SystemRole.TENANT_ADMIN.code());
        long bossAssignment = asBoss(() -> assignments.findByRoleIdAndSubjectTypeAndSubjectId(tenantAdmin, SubjectType.USER,
                catalog.boss)).orElseThrow().requireId();

        assertRefused(() -> assign(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED),
                AuthzErrorCode.ASSIGNMENT_EXISTS);
        assertRefused(() -> assign(auditors, SubjectType.GROUP, people.dev, new RoleAssignment.Terms(NOW, NOW, false)),
                AuthzErrorCode.ASSIGNMENT_PERIOD_INVALID);
        assertRefused(() -> assign(auditors, SubjectType.POSITION, 42, RoleAssignment.Terms.UNLIMITED), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> assign(42, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> asBoss(() -> service.change(catalog.boss, bossAssignment, RoleAssignment.Terms.UNLIMITED)),
                AuthzErrorCode.ASSIGNMENT_PROTECTED);
        assertRefused(() -> asBoss(() -> {
            service.remove(catalog.boss, bossAssignment);
            return null;
        }), AuthzErrorCode.ASSIGNMENT_PROTECTED);
        // Other accounts can lose the tenant administrator role again.
        AssignmentView aliceAdmin = assign(tenantAdmin, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        asBoss(() -> {
            service.remove(catalog.boss, aliceAdmin.id());
            return null;
        });
        assertRefused(() -> asBoss(() -> service.rolesOf(catalog.boss, 42)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> asBoss(() -> {
            service.remove(catalog.boss, 42);
            return null;
        }), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void onlyHoldersOfThePlatformAdministratorRoleGiveIt()
    {
        provisioner.provision(catalog.platform, true);
        long platformAdmin = catalog.asRoot(() -> roles.findByCode(SystemRole.PLATFORM_ADMIN.code())).orElseThrow().requireId();
        long helper = catalog.asRoot(() -> accounts.save(UserAccount.create("helper", "h",
                Instant.EPOCH)).requireId());
        long other = catalog.asRoot(() -> accounts.save(UserAccount.create("other", "h", Instant.EPOCH)).requireId());
        // An account of the platform tenant without the role cannot give it, whatever else it may do.
        assertRefused(() -> catalog.asRoot(() -> service.assign(helper, platformAdmin, SubjectType.USER, other,
                RoleAssignment.Terms.UNLIMITED)), AuthzErrorCode.ROLE_NOT_ASSIGNABLE);
        assertThat(catalog.asRoot(() -> service.assign(catalog.root, platformAdmin, SubjectType.USER, helper,
                RoleAssignment.Terms.UNLIMITED)).valid()).isTrue();
        // Holding the role now, the helper may give it in turn.
        assertThat(catalog.asRoot(() -> service.assign(helper, platformAdmin, SubjectType.USER, other,
                RoleAssignment.Terms.UNLIMITED)).valid()).isTrue();
    }

    @Test
    void deletedSubjectsLoseTheirAssignmentsAndDeletedRolesTakeTheirsAlong()
    {
        assign(auditors, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED);
        assign(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);

        catalog.inTenant(() -> {
            events.publishEvent(new IdentityDeleted(IdentityDeleted.Kind.GROUP, people.dev));
            return null;
        });
        assertThat(asBoss(() -> service.list(catalog.boss, auditors))).extracting(view -> view.subject().type())
                .containsExactly(SubjectType.USER);
        asBoss(() -> {
            roleService.delete(catalog.boss, auditors);
            return null;
        });
        assertThat(asBoss(() -> assignments.findBySubjects(SubjectType.USER, List.of(people.alice)))).isEmpty();
    }
}
