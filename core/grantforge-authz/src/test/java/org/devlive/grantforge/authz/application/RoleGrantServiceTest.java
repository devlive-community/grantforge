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
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({EffectiveRoles.class, AuthorizationEvaluator.class, SubjectDirectory.class, AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, ResourceService.class,
        RoleService.class, SystemRoleProvisioner.class, RoleGrantService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleGrantServiceTest
{
    @Autowired
    private RoleGrantService service;

    @Autowired
    private RoleService roleService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private RoleGrantRepository grants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private ResourceDependencyRepository dependencies;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

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

    private CatalogFixture fixture;
    private long console;
    private Resource users;
    private Resource edit;
    private Resource update;
    private Resource tenantsPage;
    private Resource module;
    private long auditors;

    @BeforeEach
    void createCatalog()
    {
        fixture = new CatalogFixture(tenants, accounts, platform);
        console = applicationService.registerConsole();
        Resource system = save(null, ResourceType.MODULE, "system");
        module = system;
        users = save(system, ResourceType.PAGE, "system.user");
        edit = save(users, ResourceType.ACTION, "system.user.btn.edit");
        Resource apis = save(null, ResourceType.MODULE, "api");
        update = save(apis, ResourceType.API, "api:system.user.update");
        Resource platformModule = save(null, ResourceType.MODULE, "platform");
        tenantsPage = save(platformModule, ResourceType.PAGE, "platform.tenant");
        dependencies.save(ResourceDependency.create(edit, update, DependencyKind.REQUIRED, DependencySource.DECLARED));
        provisioner.provision(fixture.tenant, false);
        provisioner.provision(fixture.platform, true);
        auditors = asBoss(() -> roleService.create(fixture.boss, "auditors", "Auditors", null)).id();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            grants.deleteAllInBatch();
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        dependencies.deleteAllInBatch();
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    private Resource save(@Nullable Resource parent, ResourceType type, String code)
    {
        return resources.save(Resource.create(console, parent, type, code, CatalogTestData.details(code), 0));
    }

    private <T> T asBoss(Supplier<T> action)
    {
        return fixture.inTenant(action);
    }

    private static Map<Long, GrantDerivation.State> states(GrantMatrix matrix)
    {
        return matrix.states().stream().collect(Collectors.toMap(GrantMatrix.State::resourceId, GrantMatrix.State::state));
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    @Test
    void previewsShowWhatChangesWouldMeanWithoutSavingThem()
    {
        GrantMatrix preview = asBoss(() -> service.preview(fixture.boss, auditors, console,
                List.of(new GrantChange(edit.requireId(), GrantEffect.ALLOW, null))));

        assertThat(states(preview)).containsEntry(edit.requireId(), GrantDerivation.State.ALLOWED)
                .containsEntry(update.requireId(), GrantDerivation.State.IMPLIED)
                .containsEntry(users.requireId(), GrantDerivation.State.IMPLIED);
        assertThat(preview.readOnly()).isFalse();
        assertThat(asBoss(() -> grants.findByRoleId(auditors))).isEmpty();
        assertThat(asBoss(() -> service.matrix(fixture.boss, auditors, console)).states()).isEmpty();
    }

    @Test
    void appliesChangesAndAuditsThem()
    {
        Instant end = Instant.parse("2099-01-01T00:00:00Z");
        GrantMatrix applied = asBoss(() -> service.apply(fixture.boss, auditors, console, List.of(
                new GrantChange(edit.requireId(), GrantEffect.ALLOW, null),
                new GrantChange(users.requireId(), GrantEffect.ALLOW, end))));

        assertThat(applied.grants()).hasSize(2);
        assertThat(applied.grants()).filteredOn(grant -> grant.resourceId() == users.requireId()).singleElement()
                .satisfies(grant -> assertThat(grant.expiresAt()).isEqualTo(end));
        // Change one grant in place and take the other back.
        GrantMatrix changed = asBoss(() -> service.apply(fixture.boss, auditors, console, List.of(
                new GrantChange(users.requireId(), GrantEffect.DENY, null),
                new GrantChange(edit.requireId(), null, null))));
        assertThat(changed.grants()).extracting(GrantMatrix.Grant::effect).containsExactly(GrantEffect.DENY);
        assertThat(states(changed)).containsEntry(edit.requireId(), GrantDerivation.State.DENIED);
        assertThat(asBoss(() -> grants.findByRoleId(auditors))).singleElement()
                .satisfies(grant -> assertThat(grant.getGrantedBy()).isEqualTo(fixture.boss));
        assertThat(CatalogFixture.trail(events)).filteredOn(line -> line.startsWith("ROLE_GRANTS_CHANGED"))
                .containsExactly("ROLE_GRANTS_CHANGED:" + fixture.boss + ":2", "ROLE_GRANTS_CHANGED:" + fixture.boss + ":2");
    }

    @Test
    void copiesTakeTheGrantsAlongAndDeletedRolesTakeThemAway()
    {
        asBoss(() -> service.apply(fixture.boss, auditors, console, List.of(new GrantChange(edit.requireId(), GrantEffect.ALLOW, null))));
        long copy = asBoss(() -> roleService.copy(fixture.boss, auditors, "auditors-2", "Copy")).id();

        assertThat(asBoss(() -> service.matrix(fixture.boss, copy, console)).grants()).hasSize(1);
        // A granted resource cannot be deleted from the catalog.
        assertRefused(() -> fixture.asRoot(() -> {
            resourceService.delete(fixture.root, edit.requireId());
            return null;
        }), AuthzErrorCode.RESOURCE_GRANTED);
        asBoss(() -> {
            roleService.delete(fixture.boss, auditors);
            roleService.delete(fixture.boss, copy);
            return null;
        });
        assertThat(TenantContext.callAsSystem(() -> grants.count())).isZero();
    }

    @Test
    void systemRolesAllowTheirModulesAndCannotBeChanged()
    {
        long admin = asBoss(() -> roles.findByCode(SystemRole.TENANT_ADMIN.code())).orElseThrow().requireId();
        GrantMatrix matrix = asBoss(() -> service.matrix(fixture.boss, admin, console));

        assertThat(matrix.readOnly()).isTrue();
        assertThat(states(matrix)).containsEntry(module.requireId(), GrantDerivation.State.IMPLIED)
                .containsEntry(edit.requireId(), GrantDerivation.State.IMPLIED)
                .containsEntry(update.requireId(), GrantDerivation.State.IMPLIED)
                .doesNotContainKey(tenantsPage.requireId());
        assertRefused(() -> asBoss(() -> service.apply(fixture.boss, admin, console, List.of())), AuthzErrorCode.ROLE_PROTECTED);
        long platformAdmin = fixture.asRoot(() -> roles.findByCode(SystemRole.PLATFORM_ADMIN.code())).orElseThrow().requireId();
        assertThat(states(fixture.asRoot(() -> service.matrix(fixture.root, platformAdmin, console))))
                .containsKey(tenantsPage.requireId());
    }

    @Test
    void refusesModulesPlatformResourcesOutsideThePlatformAndUnknownThings()
    {
        assertRefused(() -> asBoss(() -> service.apply(fixture.boss, auditors, console,
                List.of(new GrantChange(module.requireId(), GrantEffect.ALLOW, null)))), AuthzErrorCode.GRANT_TYPE_UNSUPPORTED);
        assertRefused(() -> asBoss(() -> service.preview(fixture.boss, auditors, console,
                List.of(new GrantChange(tenantsPage.requireId(), GrantEffect.ALLOW, null)))), AuthzErrorCode.GRANT_NOT_ALLOWED);
        long platformRole = fixture.asRoot(() -> roleService.create(fixture.root, "operators", "Operators", null)).id();
        assertThat(fixture.asRoot(() -> service.apply(fixture.root, platformRole, console,
                List.of(new GrantChange(tenantsPage.requireId(), GrantEffect.ALLOW, null)))).grants()).hasSize(1);
        assertRefused(() -> asBoss(() -> service.apply(fixture.boss, auditors, console,
                List.of(new GrantChange(42, GrantEffect.ALLOW, null)))), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> asBoss(() -> service.matrix(fixture.boss, auditors, 42)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> asBoss(() -> service.matrix(fixture.boss, 42, console)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> asBoss(() -> service.matrix(fixture.member, auditors, console)), CommonErrorCode.FORBIDDEN);
        long crm = fixture.asRoot(() -> applicationService.create(fixture.root, "crm", "CRM", null)).id();
        assertRefused(() -> asBoss(() -> service.apply(fixture.boss, auditors, crm,
                List.of(new GrantChange(edit.requireId(), GrantEffect.ALLOW, null)))), CommonErrorCode.NOT_FOUND);
        assertThat(applications.findByCode(Application.CONSOLE)).isPresent();
    }
}
