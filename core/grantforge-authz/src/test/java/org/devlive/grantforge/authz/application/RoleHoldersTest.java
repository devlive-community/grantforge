// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.error.ErrorCode;
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
import org.jspecify.annotations.Nullable;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({SodService.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, RoleService.class,
        SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class, AuthorizationVersions.class,
        RoleAssignmentService.class, RoleGrantService.class, ImpactAnalysis.class, RoleHolders.class, RoleService.class, RoleInheritanceService.class, RoleHolders.class, RoleHoldersTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleHoldersTest
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
    private AuthorizationEvaluator evaluator;

    @Autowired
    private RoleHolders holders;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleParentRepository parents;

    @Autowired
    private RoleAssignmentService assignmentService;

    @Autowired
    private RoleGrantService grantService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleGrantRepository grants;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private ResourceDependencyRepository dependencies;

    @Autowired
    private ApplicationRepository applications;

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
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PlatformAdministrators platform;

    private CatalogFixture catalog;
    private AssignmentFixture people;
    private long console;
    private Resource users;
    private Resource edit;
    private Resource delete;
    private Resource update;
    private Resource tenantsPage;
    private Resource reports;

    @BeforeEach
    void createCatalogAndPeople()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        console = applicationService.registerConsole();
        Resource system = save(null, ResourceType.MODULE, "system");
        users = save(system, ResourceType.PAGE, "system.user");
        edit = save(users, ResourceType.ACTION, "system.user.btn.edit");
        delete = save(users, ResourceType.ACTION, "system.user.btn.delete");
        Resource apis = save(null, ResourceType.MODULE, "api");
        update = save(apis, ResourceType.API, "api:system.user.update");
        Resource platformModule = save(null, ResourceType.MODULE, "platform");
        tenantsPage = save(platformModule, ResourceType.PAGE, "platform.tenant");
        Resource extra = save(null, ResourceType.MODULE, "extra");
        reports = save(extra, ResourceType.PAGE, "extra.reports");
        dependencies.save(ResourceDependency.create(edit, update, DependencyKind.REQUIRED, DependencySource.DECLARED));
        provisioner.provision(catalog.tenant, false);
        provisioner.provision(catalog.platform, true);
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            parents.deleteAllInBatch();
            grants.deleteAllInBatch();
            return null;
        });
        AssignmentFixture.deleteRows(assignments, roles, groupMembers, unitMembers, holdings, groups, units, positions);
        dependencies.deleteAllInBatch();
        catalog.deleteRows(resources, applications, events, transactionManager);
    }

    private Resource save(@Nullable Resource parent, ResourceType type, String code)
    {
        return resources.save(Resource.create(console, parent, type, code, CatalogTestData.details(code), 0));
    }

    private long role(String code, boolean enabled, Object... grantsAndEffects)
    {
        return catalog.inTenant(() -> {
            Role created = Role.create(code, code, null);
            created.enable(enabled);
            long id = roles.save(created).requireId();
            for (int i = 0; i < grantsAndEffects.length; i += 2) {
                grants.save(RoleGrant.create(id, (Resource) grantsAndEffects[i], (GrantEffect) grantsAndEffects[i + 1], null, 1));
            }
            return id;
        });
    }

    private void give(long role, SubjectType type, long subject, RoleAssignment.Terms terms)
    {
        catalog.inTenant(() -> assignments.save(RoleAssignment.create(role, type, subject, terms)));
    }

    private AuthorizationSnapshot snapshotOf(long account)
    {
        return catalog.inTenant(() -> evaluator.snapshot(account));
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    private Set<Long> holdersOf(long... roleIds)
    {
        return catalog.inTenant(() -> new TransactionTemplate(transactionManager).execute(status ->
                holders.of(LongStream.of(roleIds).boxed().toList(), NOW)));
    }

    @Test
    void findsHoldersDirectlyAndThroughGroupsDepartmentsAndPositions()
    {
        long direct = role("direct", true);
        long byGroup = role("by-group", true);
        long byUnit = role("by-unit", true);
        long bySubUnits = role("by-sub-units", true);
        long byParentOnly = role("by-parent-only", true);
        long byPosition = role("by-position", true);
        long expired = role("expired", true);
        give(direct, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        give(byGroup, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED);
        give(byUnit, SubjectType.ORG_UNIT, people.sales, RoleAssignment.Terms.UNLIMITED);
        give(bySubUnits, SubjectType.ORG_UNIT, people.hq, new RoleAssignment.Terms(null, null, true));
        give(byParentOnly, SubjectType.ORG_UNIT, people.hq, RoleAssignment.Terms.UNLIMITED);
        give(byPosition, SubjectType.POSITION, people.cfo, RoleAssignment.Terms.UNLIMITED);
        give(expired, SubjectType.USER, people.alice, new RoleAssignment.Terms(null, NOW, false));

        for (long held : List.of(direct, byGroup, byUnit, bySubUnits, byPosition)) {
            assertThat(holdersOf(held)).containsExactly(people.alice);
        }
        // Alice is in sales below hq: an assignment to hq alone does not reach her, nor does an expired one.
        assertThat(holdersOf(byParentOnly)).isEmpty();
        assertThat(holdersOf(expired)).isEmpty();
        assertThat(holdersOf(direct, byGroup, byPosition)).containsExactly(people.alice);
        assertThat(holdersOf()).isEmpty();
    }

    @Test
    void findsTheAccountsAnAssignmentToASubjectReaches()
    {
        Function<Supplier<Set<Long>>, Set<Long>> in =
                work -> catalog.inTenant(() -> new TransactionTemplate(transactionManager).execute(status -> work.get()));

        assertThat(in.apply(() -> holders.ofSubject(SubjectType.USER, people.alice, false))).containsExactly(people.alice);
        assertThat(in.apply(() -> holders.ofSubject(SubjectType.GROUP, people.dev, false))).containsExactly(people.alice);
        assertThat(in.apply(() -> holders.ofSubject(SubjectType.POSITION, people.cfo, false))).containsExactly(people.alice);
        assertThat(in.apply(() -> holders.ofSubject(SubjectType.ORG_UNIT, people.sales, false))).containsExactly(people.alice);
        // Alice is in sales below hq.
        assertThat(in.apply(() -> holders.ofSubject(SubjectType.ORG_UNIT, people.hq, false))).isEmpty();
        assertThat(in.apply(() -> holders.ofSubject(SubjectType.ORG_UNIT, people.hq, true))).containsExactly(people.alice);
    }
}
