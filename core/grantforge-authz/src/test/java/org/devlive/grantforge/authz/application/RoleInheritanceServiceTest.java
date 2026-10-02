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
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, RoleService.class,
        SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class,
        RoleAssignmentService.class, RoleGrantService.class, RoleService.class, RoleInheritanceService.class, RoleInheritanceServiceTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleInheritanceServiceTest
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

    private static List<String> codes(List<RoleInheritance.Related> related)
    {
        return related.stream().map(item -> item.role().code() + ":" + item.distance()).toList();
    }

    @Test
    void rolesInheritWhatTheirEnabledParentsAllowAndWhatTheyInheritInTurn()
    {
        long editors = role("editors", true, edit, GrantEffect.ALLOW);
        long viewers = role("viewers", true);
        long base = role("base", true);
        long tenantAdmin = catalog.inTenant(() -> roles.findByCode("tenant-admin")).orElseThrow().requireId();
        give(base, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        assertThat(snapshotOf(people.alice).resources()).isEmpty();

        RoleInheritance viewing = inherit(viewers, editors);
        assertThat(viewing.parents()).extracting(RoleView::code).containsExactly("editors");
        inherit(base, viewers);
        RoleInheritance basic = catalog.inTenant(() -> inheritance.of(base));
        assertThat(codes(basic.ancestors())).containsExactly("viewers:1", "editors:2");
        assertThat(codes(catalog.inTenant(() -> inheritance.of(editors)).descendants())).containsExactly("viewers:1", "base:2");
        assertThat(catalog.inTenant(() -> inheritance.links())).hasSize(2);

        AuthorizationSnapshot inherited = snapshotOf(people.alice);
        assertThat(inherited.roles()).containsExactly("base", "viewers", "editors");
        assertThat(inherited.resources()).contains("system.user.btn.edit");
        assertThat(inherited.permissions()).containsExactly("system.user.update");

        // A disabled role passes nothing on, not even what it inherits.
        catalog.inTenant(() -> roleService.enable(catalog.boss, viewers, false));
        assertThat(snapshotOf(people.alice).resources()).isEmpty();
        catalog.inTenant(() -> roleService.enable(catalog.boss, viewers, true));

        // Custom roles may inherit a system role's modules; system roles inherit from nothing.
        inherit(viewers, editors, tenantAdmin);
        assertThat(snapshotOf(people.alice).resources()).contains("system.user.btn.delete");
        assertRefused(() -> inherit(tenantAdmin, editors), AuthzErrorCode.ROLE_PROTECTED);
    }

    @Test
    void refusesCyclesUnknownRolesTooManyParentsAndMoreThanTheActorHas()
    {
        long editors = role("editors", true, edit, GrantEffect.ALLOW);
        long viewers = role("viewers", true);
        long reporters = role("reporters", true, reports, GrantEffect.ALLOW);
        inherit(viewers, editors);

        assertRefused(() -> inherit(editors, viewers), AuthzErrorCode.ROLE_INHERITANCE_CYCLE);
        assertRefused(() -> inherit(editors, editors), AuthzErrorCode.ROLE_INHERITANCE_CYCLE);
        assertRefused(() -> inherit(editors, 42L), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> catalog.inTenant(() -> inheritance.of(42)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> inherit(viewers, LongStream.rangeClosed(1, RoleInheritanceService.MAX_PARENTS + 1)
                .boxed().toArray(Long[]::new)), CommonErrorCode.BAD_REQUEST);
        // The tenant administrator has the access-control module, not the extra one.
        assertRefused(() -> inherit(viewers, editors, reporters), AuthzErrorCode.ROLE_EXCEEDS_ACTOR);
        // Replacing the parents: a role may take the place of its own former parent's position without a cycle.
        assertThat(inherit(viewers).parents()).isEmpty();
        assertThat(inherit(editors, viewers).parents()).extracting(RoleView::code).containsExactly("viewers");
    }

    @Test
    void copiesTakeTheParentsAlongDeletionsTheLinksAndChangesAreAudited()
    {
        long editors = role("editors", true, edit, GrantEffect.ALLOW);
        long viewers = role("viewers", true);
        inherit(viewers, editors);
        inherit(viewers, editors);
        long copy = catalog.inTenant(() -> roleService.copy(catalog.boss, viewers, "viewers-copy", "Copy")).id();
        assertThat(catalog.inTenant(() -> inheritance.of(copy)).parents()).extracting(RoleView::code).containsExactly("editors");

        catalog.inTenant(() -> {
            roleService.delete(catalog.boss, editors);
            return null;
        });
        assertThat(catalog.inTenant(() -> inheritance.links())).isEmpty();
        assertThat(CatalogFixture.trail(events)).filteredOn(entry -> entry.startsWith("ROLE_PARENTS_CHANGED"))
                .containsExactly("ROLE_PARENTS_CHANGED:" + catalog.boss + ":editors");
    }
}
