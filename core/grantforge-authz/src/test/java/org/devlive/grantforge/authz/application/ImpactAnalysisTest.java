// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceDetails;
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
import org.devlive.grantforge.common.error.CommonErrorCode;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, RoleService.class,
        SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class, AuthorizationVersions.class,
        RoleAssignmentService.class, RoleGrantService.class, ImpactAnalysis.class, RoleHolders.class, RoleService.class, RoleInheritanceService.class, ImpactAnalysisTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImpactAnalysisTest
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
    private ImpactAnalysis impacts;

    @Autowired
    private RoleInheritanceService inheritance;

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

    private RoleInheritance inherit(long role, Long... parentIds)
    {
        return catalog.inTenant(() -> inheritance.setParents(catalog.boss, role, List.of(parentIds)));
    }

    private static List<String> rolesOf(ImpactReport report)
    {
        return report.roles().stream().map(role -> role.tenantCode() + ":" + role.code() + " +" + role.gained() + " -" + role.lost())
                .toList();
    }

    @Test
    void takingBackAGrantReachesTheRoleAndTheRolesInheritingFromIt()
    {
        long auditors = role("auditors", true, edit, GrantEffect.ALLOW);
        long viewers = role("viewers", true);
        role("deleters", true, delete, GrantEffect.ALLOW);
        inherit(viewers, auditors);
        give(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        give(viewers, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED);

        ImpactReport report = catalog.inTenant(() -> grantService.impact(catalog.boss, auditors, console,
                List.of(new GrantChange(edit.requireId(), null, null))));

        assertThat(rolesOf(report)).containsExactly("null:auditors +0 -5", "null:viewers +0 -5");
        assertThat(report.lost()).containsExactly("api", "api:system.user.update", "system", "system.user", "system.user.btn.edit");
        assertThat(report.gained()).isEmpty();
        // Alice holds both roles, directly and through her group, and counts once.
        assertThat(report.accounts()).isEqualTo(1);
        // Nothing changes, nothing is reported.
        assertThat(catalog.inTenant(() -> grantService.impact(catalog.boss, auditors, console, List.of())).roles()).isEmpty();
    }

    @Test
    void catalogChangesReachRolesOfEveryTenant()
    {
        long auditors = role("auditors", true, edit, GrantEffect.ALLOW);
        role("deleters", true, delete, GrantEffect.ALLOW);
        give(auditors, SubjectType.POSITION, people.cfo, RoleAssignment.Terms.UNLIMITED);
        long requirement = dependencies.findByResourceId(edit.requireId()).get(0).requireId();

        ImpactReport disabling = impacts.ofEnabled(edit.requireId(), false);
        assertThat(rolesOf(disabling)).contains("acme:auditors +0 -5", "acme:tenant-admin +0 -3", "platform:platform-admin +0 -3");
        assertThat(disabling.lost()).contains("system.user.btn.edit");
        assertThat(disabling.accounts()).isEqualTo(3);
        assertThat(impacts.ofEnabled(edit.requireId(), true).roles()).isEmpty();

        ImpactReport needing = impacts.ofNewDependency(delete.requireId(), update.requireId(), DependencyKind.REQUIRED);
        // Deleters have the button already; they gain the API it would need, and its module.
        assertThat(rolesOf(needing)).containsExactly("acme:deleters +2 -0");
        assertThat(needing.gained()).containsExactly("api", "api:system.user.update");
        assertThat(impacts.ofNewDependency(delete.requireId(), update.requireId(), DependencyKind.OPTIONAL).roles()).isEmpty();

        ImpactReport removing = impacts.ofDependencyChange(requirement, null);
        // Everyone with the edit button had the API only through it, system roles of both tenants included.
        assertThat(rolesOf(removing)).containsExactlyInAnyOrder("acme:auditors +0 -2", "acme:tenant-admin +0 -2",
                "platform:tenant-admin +0 -2", "platform:platform-admin +0 -2");
        assertThat(removing.lost()).containsExactly("api", "api:system.user.update");
        assertThat(impacts.ofDependencyChange(requirement, DependencyKind.OPTIONAL).lost()).containsExactly("api", "api:system.user.update");
        assertThat(impacts.ofDependencyChange(requirement, DependencyKind.REQUIRED).roles()).isEmpty();

        assertRefused(() -> impacts.ofEnabled(42, false), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> impacts.ofDependencyChange(42, null), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> impacts.ofNewDependency(update.requireId(), edit.requireId(), DependencyKind.REQUIRED),
                CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void disabledResourcesAndEverythingBelowThemGrantNothing()
    {
        long auditors = role("auditors", true, edit, GrantEffect.ALLOW);
        give(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assertThat(snapshotOf(people.alice).resources()).contains("system.user.btn.edit");

        users.update(new ResourceDetails("system.user", null, null, true, false, DenyMode.HIDE));
        resources.save(users);

        AuthorizationSnapshot snapshot = snapshotOf(people.alice);
        assertThat(snapshot.resources()).doesNotContain("system.user", "system.user.btn.edit");
        // Nor does it bring along what it needs.
        assertThat(snapshot.permissions()).isEmpty();
    }
}
