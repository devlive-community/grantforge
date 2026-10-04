// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
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
import org.devlive.grantforge.authz.domain.RoleParent;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.error.CommonErrorCode;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, SystemRoleProvisioner.class,
        SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class, AuthorizationVersions.class, AuthorizationInsight.class,
        AuthorizationEvaluatorTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorizationInsightTest
{
    @Autowired
    private AuthorizationInsight insight;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleParentRepository parents;

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
    private Resource reports;

    @BeforeEach
    void createCatalogAndPeople()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        console = applicationService.registerConsole();
        Resource system = save(null, ResourceType.MODULE, "system", true);
        users = save(system, ResourceType.PAGE, "system.user", true);
        edit = save(users, ResourceType.ACTION, "system.user.btn.edit", true);
        delete = save(users, ResourceType.ACTION, "system.user.btn.delete", true);
        Resource apis = save(null, ResourceType.MODULE, "api", true);
        Resource update = save(apis, ResourceType.API, "api:system.user.update", true);
        Resource extra = save(null, ResourceType.MODULE, "extra", false);
        reports = save(extra, ResourceType.PAGE, "extra.reports", true);
        dependencies.save(ResourceDependency.create(edit, update, DependencyKind.REQUIRED, DependencySource.DECLARED));
        provisioner.provision(catalog.tenant, false);
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            grants.deleteAllInBatch();
            parents.deleteAllInBatch();
            return null;
        });
        AssignmentFixture.deleteRows(assignments, roles, groupMembers, unitMembers, holdings, groups, units, positions);
        dependencies.deleteAllInBatch();
        catalog.deleteRows(resources, applications, events, transactionManager);
    }

    private Resource save(@Nullable Resource parent, ResourceType type, String code, boolean enabled)
    {
        return resources.save(Resource.create(console, parent, type, code, new ResourceDetails(code, null, null, true, enabled,
                DenyMode.HIDE), 0));
    }

    private long role(String code, Object... grantsAndEffects)
    {
        return catalog.inTenant(() -> {
            long id = roles.save(Role.create(code, code, null)).requireId();
            for (int i = 0; i < grantsAndEffects.length; i += 2) {
                grants.save(RoleGrant.create(id, (Resource) grantsAndEffects[i], (GrantEffect) grantsAndEffects[i + 1], null, 1));
            }
            return id;
        });
    }

    private void give(long role, SubjectType type, long subject)
    {
        catalog.inTenant(() -> assignments.save(RoleAssignment.create(role, type, subject, RoleAssignment.Terms.UNLIMITED)));
    }

    private AccessExplanation explain(long account, AccessKind kind, String code)
    {
        return catalog.inTenant(() -> insight.explain(catalog.boss, account, new AccessCheck(kind, code)));
    }

    @Test
    void explainsEveryPathFromAHeldRoleToTheResource()
    {
        long base = role("base", edit, GrantEffect.ALLOW);
        long editors = role("editors");
        catalog.inTenant(() -> parents.save(RoleParent.of(editors, base)));
        give(editors, SubjectType.GROUP, people.dev);
        long direct = role("direct", users, GrantEffect.ALLOW);
        give(direct, SubjectType.USER, people.alice);

        AccessExplanation api = explain(people.alice, AccessKind.PERMISSION, "system.user.update");
        assertThat(api.outcome()).isEqualTo(AccessExplanation.Outcome.ALLOWED);
        assertThat(api.name()).isEqualTo("api:system.user.update");
        assertThat(api.paths()).singleElement().satisfies(path -> {
            assertThat(path.roles()).extracting(AccessExplanation.PathRole::code).containsExactly("editors", "base");
            assertThat(path.roles().get(0).assignedTo()).extracting(Subject::type, Subject::id).containsExactly(tuple(SubjectType.GROUP, people.dev));
            assertThat(path.roles().get(1).assignedTo()).isEmpty();
            assertThat(path.resources()).extracting(AccessExplanation.PathResource::code, AccessExplanation.PathResource::via).containsExactly(
                    tuple("system.user.btn.edit", AccessExplanation.Via.GRANT), tuple("api:system.user.update", AccessExplanation.Via.DEPENDENCY));
        });

        AccessExplanation page = explain(people.alice, AccessKind.RESOURCE, "system.user");
        assertThat(page.paths()).hasSize(2);
        assertThat(page.paths()).filteredOn(path -> path.roles().get(0).code().equals("editors")).singleElement()
                .satisfies(path -> assertThat(path.resources()).extracting(AccessExplanation.PathResource::via)
                        .containsExactly(AccessExplanation.Via.GRANT, AccessExplanation.Via.ANCESTOR));
        assertThat(page.paths()).filteredOn(path -> path.roles().get(0).code().equals("direct")).singleElement()
                .satisfies(path -> assertThat(path.resources()).extracting(AccessExplanation.PathResource::code).containsExactly("system.user"));
    }

    @Test
    void namesDenialsAndWhatIsNotGrantedDisabledOrUnknown()
    {
        long editors = role("editors", edit, GrantEffect.ALLOW, reports, GrantEffect.ALLOW);
        give(editors, SubjectType.USER, people.alice);
        long noUsers = role("no-users", users, GrantEffect.DENY);
        give(noUsers, SubjectType.POSITION, people.cfo);

        AccessExplanation denied = explain(people.alice, AccessKind.RESOURCE, "system.user.btn.edit");
        assertThat(denied.outcome()).isEqualTo(AccessExplanation.Outcome.DENIED);
        assertThat(denied.denials()).extracting(AccessExplanation.Denial::roleCode, AccessExplanation.Denial::resourceCode)
                .containsExactly(tuple("no-users", "system.user"));
        // The role that allows it is listed too: the denial wins over it.
        assertThat(denied.paths()).extracting(path -> path.roles().get(0).code()).containsExactly("editors");
        assertThat(explain(people.alice, AccessKind.RESOURCE, "system.user").denials()).extracting(AccessExplanation.Denial::resourceCode)
                .containsExactly("system.user");

        assertThat(explain(people.alice, AccessKind.RESOURCE, "extra.reports").outcome()).isEqualTo(AccessExplanation.Outcome.DISABLED);
        assertThat(explain(catalog.member, AccessKind.RESOURCE, "system.user.btn.delete").outcome())
                .isEqualTo(AccessExplanation.Outcome.NOT_GRANTED);
        AccessExplanation unknown = explain(people.alice, AccessKind.PERMISSION, "system.nope");
        assertThat(unknown.outcome()).isEqualTo(AccessExplanation.Outcome.UNKNOWN);
        assertThat(unknown.name()).isNull();
        // A console resource is not an API permission of the same code.
        assertThat(explain(people.alice, AccessKind.PERMISSION, "system.user").outcome()).isEqualTo(AccessExplanation.Outcome.UNKNOWN);
    }

    @Test
    void systemRolesAllowTheirModulesAsAWhole()
    {
        AccessExplanation boss = explain(catalog.boss, AccessKind.RESOURCE, "system.user.btn.delete");
        assertThat(boss.outcome()).isEqualTo(AccessExplanation.Outcome.ALLOWED);
        assertThat(boss.paths()).singleElement().satisfies(path -> {
            assertThat(path.roles()).extracting(AccessExplanation.PathRole::code).containsExactly("tenant-admin");
            assertThat(path.resources()).extracting(AccessExplanation.PathResource::code, AccessExplanation.PathResource::via)
                    .containsExactly(tuple("system.user.btn.delete", AccessExplanation.Via.SYSTEM_ROLE));
        });
    }

    @Test
    void checksManyQuestionsAboutAccountsTheActorSees()
    {
        long editors = role("editors", edit, GrantEffect.ALLOW);
        give(editors, SubjectType.USER, people.alice);
        List<AccessResult> results = catalog.inTenant(() -> insight.check(catalog.boss, people.alice, List.of(
                new AccessCheck(AccessKind.RESOURCE, "system.user.btn.edit"), new AccessCheck(AccessKind.PERMISSION, "system.user.update"),
                new AccessCheck(AccessKind.RESOURCE, "system.user.btn.delete"), new AccessCheck(AccessKind.PERMISSION, "system.user.btn.edit"))));
        assertThat(results).extracting(AccessResult::allowed).containsExactly(true, true, false, false);

        List<AccessCheck> many = Collections.nCopies(AuthorizationInsight.MAX_CHECKS + 1, new AccessCheck(AccessKind.RESOURCE, "system"));
        assertThatThrownBy(() -> catalog.inTenant(() -> insight.check(catalog.boss, people.alice, many)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        // An account of another tenant cannot be seen from this one.
        assertThatThrownBy(() -> catalog.inTenant(() -> insight.check(catalog.boss, catalog.root, List.of())))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> catalog.inTenant(() -> insight.explain(catalog.boss, catalog.root, new AccessCheck(AccessKind.RESOURCE,
                "system")))).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void listsEverythingAnAccountMayUse()
    {
        long editors = role("editors", edit, GrantEffect.ALLOW);
        give(editors, SubjectType.USER, people.alice);
        long unused = role("unused");
        catalog.inTenant(() -> assignments.save(RoleAssignment.create(unused, SubjectType.GROUP, people.dev,
                new RoleAssignment.Terms(null, AuthorizationEvaluatorTest.NOW, false))));

        EffectiveAccess access = catalog.inTenant(() -> insight.effective(catalog.boss, people.alice));

        assertThat(access.roles()).extracting(role -> role.role().code(), EffectiveRole::active)
                .containsExactly(tuple("editors", true), tuple("unused", false));
        assertThat(access.resources()).extracting(EffectiveAccess.Item::code, EffectiveAccess.Item::parentCode).containsExactlyInAnyOrder(
                tuple("system", null), tuple("system.user", "system"), tuple("system.user.btn.edit", "system.user"), tuple("api", null));
        assertThat(access.permissions()).extracting(EffectiveAccess.Item::code, EffectiveAccess.Item::type)
                .containsExactly(tuple("system.user.update", ResourceType.API));
        assertThatThrownBy(() -> catalog.inTenant(() -> insight.effective(catalog.boss, catalog.root)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    private SimulationResult simulate(long account, Simulation simulation)
    {
        return catalog.inTenant(() -> insight.simulate(catalog.boss, account, simulation));
    }

    @Test
    void simulatesRolesAndGrantsWithoutChangingAnything()
    {
        long editors = role("editors", edit, GrantEffect.ALLOW);
        give(editors, SubjectType.USER, people.alice);
        long deleters = role("deleters", delete, GrantEffect.ALLOW);
        long off = catalog.inTenant(() -> {
            Role created = Role.create("off", "off", null);
            created.enable(false);
            return roles.save(created).requireId();
        });

        SimulationResult removed = simulate(people.alice, new Simulation(List.of(), List.of(editors), List.of()));
        assertThat(removed.rolesBefore()).containsExactly("editors");
        assertThat(removed.rolesAfter()).isEmpty();
        assertThat(removed.lostResources()).extracting(EffectiveAccess.Item::code)
                .containsExactlyInAnyOrder("system", "system.user", "system.user.btn.edit", "api");
        assertThat(removed.lostPermissions()).extracting(EffectiveAccess.Item::code).containsExactly("system.user.update");
        assertThat(removed.gainedResources()).isEmpty();

        SimulationResult added = simulate(people.alice, new Simulation(List.of(deleters, off), List.of(), List.of()));
        assertThat(added.rolesAfter()).containsExactlyInAnyOrder("editors", "deleters");
        assertThat(added.gainedResources()).extracting(EffectiveAccess.Item::code).containsExactly("system.user.btn.delete");

        SimulationResult changed = simulate(people.alice, new Simulation(List.of(), List.of(), List.of(
                new Simulation.RoleGrantChange(editors, new GrantChange(edit.requireId(), null, null)),
                new Simulation.RoleGrantChange(editors, new GrantChange(delete.requireId(), GrantEffect.ALLOW, null)),
                new Simulation.RoleGrantChange(editors, new GrantChange(reports.requireId(), GrantEffect.ALLOW, null)))));
        assertThat(changed.gainedResources()).extracting(EffectiveAccess.Item::code).containsExactly("system.user.btn.delete");
        assertThat(changed.lostResources()).extracting(EffectiveAccess.Item::code).containsExactlyInAnyOrder("api", "system.user.btn.edit");
        assertThat(changed.lostPermissions()).extracting(EffectiveAccess.Item::code).containsExactly("system.user.update");
        // Nothing was stored.
        assertThat(catalog.inTenant(() -> grants.findByRoleId(editors))).hasSize(1);
    }

    @Test
    void refusesSimulationsThatDoNotFit()
    {
        long editors = role("editors", edit, GrantEffect.ALLOW);
        long system = catalog.inTenant(() -> resources.findByApplicationIdAndCode(console, "system").orElseThrow().requireId());
        assertThatThrownBy(() -> simulate(people.alice, new Simulation(List.of(424242L), List.of(), List.of())))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> simulate(people.alice, new Simulation(List.of(), List.of(), List.of(
                new Simulation.RoleGrantChange(editors, new GrantChange(424242L, GrantEffect.ALLOW, null))))))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> simulate(people.alice, new Simulation(List.of(), List.of(), List.of(
                new Simulation.RoleGrantChange(editors, new GrantChange(system, GrantEffect.ALLOW, null))))))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        List<Long> many = Collections.nCopies(AuthorizationInsight.MAX_CHANGES + 1, editors);
        assertThatThrownBy(() -> simulate(people.alice, new Simulation(many, List.of(), List.of())))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> simulate(catalog.root, new Simulation(List.of(), List.of(), List.of())))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
