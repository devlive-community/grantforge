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
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, ResourceService.class,
        DependencyService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DependencyServiceTest
{
    @Autowired
    private DependencyService service;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ApplicationService applicationService;

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

    @BeforeEach
    void createAccounts()
    {
        fixture = new CatalogFixture(tenants, accounts, platform);
        console = applicationService.registerConsole();
    }

    @AfterEach
    void deleteRows()
    {
        dependencies.deleteAllInBatch();
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    private long create(@Nullable Long parent, ResourceType type, String code)
    {
        return fixture.asRoot(() -> resourceService.create(fixture.root, console, parent, type, code,
                CatalogTestData.details(code))).id();
    }

    private DependencyView add(long from, long to, DependencyKind kind)
    {
        return fixture.asRoot(() -> service.add(fixture.root, from, to, kind));
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    @Test
    void platformAdministratorsLinkPagesButtonsAndApisAndEveryChangeIsAudited()
    {
        long page = create(null, ResourceType.PAGE, "users");
        long edit = create(page, ResourceType.ACTION, "users.edit");
        long view = create(page, ResourceType.ACTION, "users.view");
        long read = create(null, ResourceType.API, "api:system.user.read");
        long update = create(null, ResourceType.API, "api:system.user.update");

        DependencyView pageNeedsRead = add(page, read, DependencyKind.REQUIRED);
        add(edit, update, DependencyKind.REQUIRED);
        DependencyView editNeedsView = add(edit, view, DependencyKind.OPTIONAL);
        add(view, read, DependencyKind.REQUIRED);

        assertThat(pageNeedsRead.source()).isEqualTo(DependencySource.MANUAL);
        ResourceDependencies aroundRead = fixture.asRoot(() -> service.of(fixture.root, read));
        assertThat(aroundRead.requires()).isEmpty();
        assertThat(aroundRead.requiredBy()).extracting(DependencyView::resourceId).containsExactly(page, view);
        assertThat(fixture.asRoot(() -> service.of(fixture.root, edit)).requires()).extracting(DependencyView::dependsOnId)
                .containsExactly(update, view);
        assertThat(fixture.asRoot(() -> service.graph(fixture.root, console))).hasSize(4);

        assertThat(fixture.asRoot(() -> service.changeKind(fixture.root, editNeedsView.id(), DependencyKind.REQUIRED)).kind())
                .isEqualTo(DependencyKind.REQUIRED);
        fixture.asRoot(() -> {
            service.remove(fixture.root, editNeedsView.id());
            return null;
        });
        assertThat(dependencies.existsById(editNeedsView.id())).isFalse();
        assertThat(CatalogFixture.trail(events)).filteredOn(line -> line.startsWith("RESOURCE_DEPENDENCY")).containsExactly(
                "RESOURCE_DEPENDENCY_ADDED:" + fixture.root + ":" + read,
                "RESOURCE_DEPENDENCY_ADDED:" + fixture.root + ":" + update,
                "RESOURCE_DEPENDENCY_ADDED:" + fixture.root + ":" + view,
                "RESOURCE_DEPENDENCY_ADDED:" + fixture.root + ":" + read,
                "RESOURCE_DEPENDENCY_CHANGED:" + fixture.root + ":" + view,
                "RESOURCE_DEPENDENCY_REMOVED:" + fixture.root + ":" + view);
    }

    @Test
    void refusesCyclesDuplicatesAndInvalidPairs()
    {
        long page = create(null, ResourceType.PAGE, "users");
        long edit = create(page, ResourceType.ACTION, "users.edit");
        long view = create(page, ResourceType.ACTION, "users.view");
        long read = create(null, ResourceType.API, "api:system.user.read");
        long module = create(null, ResourceType.MODULE, "system");
        add(edit, view, DependencyKind.OPTIONAL);

        assertRefused(() -> add(view, edit, DependencyKind.REQUIRED), AuthzErrorCode.DEPENDENCY_CYCLE);
        assertRefused(() -> add(edit, view, DependencyKind.REQUIRED), AuthzErrorCode.DEPENDENCY_EXISTS);
        assertRefused(() -> add(read, page, DependencyKind.REQUIRED), AuthzErrorCode.DEPENDENCY_INVALID);
        assertRefused(() -> add(page, module, DependencyKind.REQUIRED), AuthzErrorCode.DEPENDENCY_INVALID);
        assertRefused(() -> add(page, page, DependencyKind.REQUIRED), AuthzErrorCode.DEPENDENCY_INVALID);
        long crm = fixture.asRoot(() -> applicationService.create(fixture.root, "crm", "CRM", null)).id();
        long other = fixture.asRoot(() -> resourceService.create(fixture.root, crm, null, ResourceType.API, "api:crm.read",
                CatalogTestData.details("CRM"))).id();
        assertRefused(() -> add(page, other, DependencyKind.REQUIRED), AuthzErrorCode.DEPENDENCY_INVALID);
        assertRefused(() -> add(page, 42, DependencyKind.REQUIRED), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> service.changeKind(fixture.root, 42, DependencyKind.REQUIRED)),
                CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> service.of(fixture.root, 42)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> service.graph(fixture.root, 42)), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void declaredDependenciesStayAndNeededResourcesCannotBeDeleted()
    {
        long page = create(null, ResourceType.PAGE, "users");
        long button = create(page, ResourceType.ACTION, "users.export");
        long api = create(null, ResourceType.API, "api:system.user.export");
        long declared = dependencies.save(ResourceDependency.create(resources.findById(button).orElseThrow(),
                resources.findById(api).orElseThrow(), DependencyKind.REQUIRED, DependencySource.DECLARED)).requireId();

        assertRefused(() -> fixture.asRoot(() -> {
            service.remove(fixture.root, declared);
            return null;
        }), AuthzErrorCode.DEPENDENCY_DECLARED);
        assertRefused(() -> fixture.asRoot(() -> {
            resourceService.delete(fixture.root, api);
            return null;
        }), AuthzErrorCode.RESOURCE_IN_USE);
        // Deleting the button takes its own dependencies along.
        fixture.asRoot(() -> {
            resourceService.delete(fixture.root, button);
            return null;
        });
        assertThat(dependencies.count()).isZero();
        fixture.asRoot(() -> {
            resourceService.delete(fixture.root, api);
            return null;
        });
    }

    @Test
    void tenantAdministratorsReadAndOthersAreTurnedAway()
    {
        long page = create(null, ResourceType.PAGE, "users");
        long api = create(null, ResourceType.API, "api:system.user.read");

        assertThat(fixture.inTenant(() -> service.of(fixture.boss, page)).requires()).isEmpty();
        assertRefused(() -> fixture.inTenant(() -> service.add(fixture.boss, page, api, DependencyKind.REQUIRED)),
                CommonErrorCode.FORBIDDEN);
        assertRefused(() -> fixture.inTenant(() -> service.graph(fixture.member, console)), CommonErrorCode.FORBIDDEN);
    }
}
