// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.Application;
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
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({SodService.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, RoleService.class,
        SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class, AuthorizationVersions.class,
        RoleAssignmentService.class, RoleGrantService.class, ImpactAnalysis.class, RoleHolders.class, AuthorizationEvaluatorTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorizationEvaluatorTest
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

    @Test
    void mergesAllActiveRolesWithDenialsWinning()
    {
        long editors = role("editors", true, edit, GrantEffect.ALLOW);
        long deleters = role("deleters", true, delete, GrantEffect.ALLOW);
        long noDelete = role("no-delete", true, delete, GrantEffect.DENY);
        long disabled = role("disabled", false, reports, GrantEffect.ALLOW);
        long expired = role("expired", true, reports, GrantEffect.ALLOW);
        give(editors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        give(deleters, SubjectType.GROUP, people.dev, RoleAssignment.Terms.UNLIMITED);
        give(noDelete, SubjectType.POSITION, people.cfo, RoleAssignment.Terms.UNLIMITED);
        give(disabled, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        give(expired, SubjectType.USER, people.alice, new RoleAssignment.Terms(null, NOW, false));

        AuthorizationSnapshot snapshot = snapshotOf(people.alice);

        assertThat(snapshot.roles()).containsExactlyInAnyOrder("editors", "deleters", "no-delete");
        assertThat(snapshot.resources()).containsExactlyInAnyOrder("system", "system.user", "system.user.btn.edit", "api");
        assertThat(snapshot.permissions()).containsExactly("system.user.update");
        assertThat(snapshot.holds("system.user.update")).isTrue();
        assertThat(snapshot.computedAt()).isEqualTo(NOW);
    }

    @Test
    void systemRolesHaveTheirModulesAndAccountsWithoutRolesHaveNothing()
    {
        AuthorizationSnapshot boss = snapshotOf(catalog.boss);
        assertThat(boss.roles()).containsExactly("tenant-admin");
        assertThat(boss.resources()).contains("system.user", "system.user.btn.delete").doesNotContain("platform.tenant", "extra.reports");
        assertThat(boss.permissions()).containsExactly("system.user.update");

        AuthorizationSnapshot root = catalog.asRoot(() -> evaluator.snapshot(catalog.root));
        assertThat(root.resources()).contains("platform.tenant", "system.user");

        AuthorizationSnapshot member = snapshotOf(catalog.member);
        assertThat(member.roles()).isEmpty();
        assertThat(member.resources()).isEmpty();
        assertThat(member.permissions()).isEmpty();
    }

    @Test
    void worksOutAnyApplicationButSystemRolesOnlyCountInTheConsole()
    {
        long crm = applications.save(Application.create("crm", "CRM", null)).requireId();
        // A module of the same code as the console's: system roles must not reach it.
        Resource crmSystem = resources.save(Resource.create(crm, null, ResourceType.MODULE, "system", CatalogTestData.details("System"), 0));
        Resource orders = resources.save(Resource.create(crm, crmSystem, ResourceType.PAGE, "crm.orders", CatalogTestData.details("Orders"), 0));
        Resource read = resources.save(Resource.create(crm, null, ResourceType.API, "orders.read", CatalogTestData.details("Read"), 1));
        long sellers = role("sellers", true, orders, GrantEffect.ALLOW, read, GrantEffect.ALLOW, edit, GrantEffect.ALLOW);
        give(sellers, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);

        AuthorizationSnapshot inCrm = catalog.inTenant(() -> evaluator.snapshot(people.alice, crm));
        assertThat(inCrm.resources()).containsExactlyInAnyOrder("system", "crm.orders");
        assertThat(inCrm.permissions()).containsExactly("orders.read");
        // The console's snapshot is apart, and cached apart.
        assertThat(snapshotOf(people.alice).resources()).contains("system.user.btn.edit").doesNotContain("crm.orders");
        assertThat(catalog.inTenant(() -> evaluator.snapshot(catalog.boss, crm)).resources()).isEmpty();
        assertThat(evaluator.snapshot(people.alice, crm).permissions()).isEmpty();
    }

    @Test
    void sharesAnApplicationsCatalogUntilItChanges()
    {
        long reporters = role("reporters", true, reports, GrantEffect.ALLOW, edit, GrantEffect.ALLOW);
        give(reporters, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        give(reporters, SubjectType.USER, catalog.member, RoleAssignment.Terms.UNLIMITED);
        assertThat(snapshotOf(people.alice).resources()).contains("extra.reports", "system.user.btn.edit");

        // Disabling a resource changes the catalog: the next snapshot works from the changed one.
        Resource stored = resources.findById(reports.requireId()).orElseThrow();
        stored.update(new ResourceDetails("Reports", null, null, true, false, DenyMode.HIDE));
        resources.save(stored);

        assertThat(snapshotOf(catalog.member).resources()).contains("system.user.btn.edit").doesNotContain("extra.reports");
        assertThat(snapshotOf(people.alice).resources()).doesNotContain("extra.reports");
    }

    @Test
    void administratorsCannotHandOutMoreThanTheyHave()
    {
        long reporters = role("reporters", true, reports, GrantEffect.ALLOW);
        long editors = role("editors", true, edit, GrantEffect.ALLOW);

        // The tenant administrator has the access-control module, not the extra one.
        assertRefused(() -> catalog.inTenant(() -> grantService.apply(catalog.boss, editors, console,
                List.of(new GrantChange(reports.requireId(), GrantEffect.ALLOW, null)))), AuthzErrorCode.GRANT_EXCEEDS_ACTOR);
        assertThat(catalog.inTenant(() -> grantService.apply(catalog.boss, editors, console,
                List.of(new GrantChange(reports.requireId(), GrantEffect.DENY, null)))).grants()).hasSize(2);
        assertRefused(() -> catalog.inTenant(() -> assignmentService.assign(catalog.boss, reporters, SubjectType.USER, people.alice,
                RoleAssignment.Terms.UNLIMITED)), AuthzErrorCode.ROLE_EXCEEDS_ACTOR);
        assertThat(catalog.inTenant(() -> assignmentService.assign(catalog.boss, editors, SubjectType.USER, people.alice,
                RoleAssignment.Terms.UNLIMITED)).valid()).isTrue();
        // A tenant administrator may give the tenant administrator role: it is within their own rights.
        long tenantAdmin = catalog.inTenant(() -> roles.findByCode("tenant-admin")).orElseThrow().requireId();
        assertThat(catalog.inTenant(() -> assignmentService.assign(catalog.boss, tenantAdmin, SubjectType.USER, people.alice,
                RoleAssignment.Terms.UNLIMITED)).valid()).isTrue();
        assertThat(tenantsPage.getCode()).isEqualTo("platform.tenant");
    }

    @Test
    void tenantAdministratorsHandOutResourcesOfOtherApplicationsThatNobodyHoldsYet()
    {
        long crm = applications.save(Application.create("crm", "CRM", null)).requireId();
        Resource leads = resources.save(Resource.create(crm, null, ResourceType.MODULE, "crm", CatalogTestData.details("CRM"), 0));
        Resource page = resources.save(Resource.create(crm, leads, ResourceType.PAGE, "crm.leads", CatalogTestData.details("Leads"), 0));
        long sellers = role("sellers", true);

        // Nobody holds the new application's resources; the tenant's administrators set its access up.
        assertThat(catalog.inTenant(() -> grantService.apply(catalog.boss, sellers, crm,
                List.of(new GrantChange(page.requireId(), GrantEffect.ALLOW, null)))).grants()).hasSize(1);
        assertThat(catalog.inTenant(() -> assignmentService.assign(catalog.boss, sellers, SubjectType.USER, people.alice,
                RoleAssignment.Terms.UNLIMITED)).valid()).isTrue();
        // Others hand out only what they hold, there as in the console.
        assertThat(grantable(people.alice, crm)).contains(page.requireId());
        assertThat(grantable(catalog.member, crm)).isEmpty();
        assertThat(grantable(catalog.boss, crm))
                .containsExactlyInAnyOrder(leads.requireId(), page.requireId());
        assertThat(grantable(catalog.boss, console))
                .doesNotContain(reports.requireId());
    }

    private Set<Long> grantable(long accountId, long applicationId)
    {
        return requireNonNull(catalog.inTenant(() -> new TransactionTemplate(transactionManager)
                .execute(status -> evaluator.grantableResources(accountId, applicationId))));
    }
}
