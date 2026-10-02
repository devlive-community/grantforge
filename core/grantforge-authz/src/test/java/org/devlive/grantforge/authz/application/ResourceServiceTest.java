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
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, ResourceService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResourceServiceTest
{
    @Autowired
    private ResourceService service;

    @Autowired
    private ApplicationService applicationService;

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
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    private ResourceView create(@Nullable ResourceView parent, ResourceType type, String code)
    {
        return fixture.asRoot(() -> service.create(fixture.root, console, parent == null ? null : parent.id(), type, code,
                CatalogTestData.details(code)));
    }

    private List<String> tree()
    {
        return fixture.asRoot(() -> service.tree(fixture.root, console)).stream()
                .map(resource -> resource.code() + "@" + resource.depth() + "#" + resource.sortOrder()).toList();
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    @Test
    void platformAdministratorsBuildTheTreeAndEveryChangeIsAudited()
    {
        ResourceView system = create(null, ResourceType.MODULE, "system");
        ResourceView users = create(system, ResourceType.PAGE, "system.user.list");
        ResourceView export = create(users, ResourceType.ACTION, "system.user.btn.export");
        ResourceView groups = create(system, ResourceType.PAGE, "system.group.list");

        assertThat(users.parentId()).isEqualTo(system.id());
        assertThat(users.applicationId()).isEqualTo(console);
        assertThat(groups.sortOrder()).isOne();
        assertThat(tree()).containsExactly("system@0#0", "system.user.list@1#0", "system.group.list@1#1",
                "system.user.btn.export@2#0");

        ResourceView renamed = fixture.asRoot(() -> service.update(fixture.root, users.id(), "system.users",
                new ResourceDetails("用户", "列表", "/admin/users", false, true, DenyMode.DISABLE)));
        assertThat(renamed.code()).isEqualTo("system.users");
        assertThat(renamed.details()).isEqualTo(new ResourceDetails("用户", "列表", "/admin/users", false, true, DenyMode.DISABLE));

        // Moving the groups page first among its siblings renumbers them.
        fixture.asRoot(() -> service.move(fixture.root, groups.id(), system.id(), 0));
        assertThat(tree()).containsExactly("system@0#0", "system.group.list@1#0", "system.users@1#1",
                "system.user.btn.export@2#0");
        fixture.asRoot(() -> {
            service.delete(fixture.root, export.id());
            return null;
        });
        assertThat(resources.existsById(export.id())).isFalse();

        assertThat(CatalogFixture.trail(events)).containsExactly(
                "RESOURCE_CREATED:" + fixture.root + ":system",
                "RESOURCE_CREATED:" + fixture.root + ":system.user.list",
                "RESOURCE_CREATED:" + fixture.root + ":system.user.btn.export",
                "RESOURCE_CREATED:" + fixture.root + ":system.group.list",
                "RESOURCE_UPDATED:" + fixture.root + ":system.users",
                "RESOURCE_MOVED:" + fixture.root + ":system.group.list",
                "RESOURCE_DELETED:" + fixture.root + ":system.user.btn.export");
    }

    @Test
    void subtreesMoveTogetherWithinTheTypeRules()
    {
        ResourceView system = create(null, ResourceType.MODULE, "system");
        ResourceView audit = create(null, ResourceType.MODULE, "audit");
        ResourceView menu = create(system, ResourceType.MENU, "menu");
        ResourceView page = create(menu, ResourceType.PAGE, "page");
        ResourceView button = create(page, ResourceType.ACTION, "button");

        ResourceView moved = fixture.asRoot(() -> service.move(fixture.root, menu.id(), audit.id(), 9));
        assertThat(moved.parentId()).isEqualTo(audit.id());
        assertThat(tree()).containsExactly("system@0#0", "audit@0#1", "menu@1#0", "page@2#0", "button@3#0");
        Resource reloaded = resources.findById(button.id()).orElseThrow();
        assertThat(reloaded.getPath()).startsWith("/" + audit.id() + "/" + menu.id() + "/");

        // A module may become top level again; a button may not leave its page for a module.
        fixture.asRoot(() -> service.move(fixture.root, menu.id(), null, 0));
        assertThat(tree()).startsWith("menu@0#0", "system@0#1", "audit@0#2");
        assertRefused(() -> fixture.asRoot(() -> service.move(fixture.root, button.id(), system.id(), 0)),
                AuthzErrorCode.RESOURCE_PLACEMENT_INVALID);
        assertRefused(() -> fixture.asRoot(() -> service.move(fixture.root, menu.id(), page.id(), 0)),
                AuthzErrorCode.RESOURCE_MOVE_CYCLE);
        assertRefused(() -> fixture.asRoot(() -> service.move(fixture.root, 42, null, 0)), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void treesCannotGrowTooDeep()
    {
        ResourceView top = create(null, ResourceType.MODULE, "m0");
        ResourceView deepest = top;
        for (int level = 1; level <= Resource.MAX_DEPTH; level++) {
            deepest = create(deepest, ResourceType.MODULE, "m" + level);
        }
        ResourceView last = deepest;
        ResourceView other = create(null, ResourceType.MODULE, "other");
        ResourceView child = create(other, ResourceType.MODULE, "child");

        assertRefused(() -> create(last, ResourceType.MODULE, "too-deep"), AuthzErrorCode.RESOURCE_TOO_DEEP);
        assertRefused(() -> fixture.asRoot(() -> service.move(fixture.root, other.id(), last.id(), 0)),
                AuthzErrorCode.RESOURCE_TOO_DEEP);
        assertThat(child.depth()).isOne();
    }

    @Test
    void refusesWrongPlacementsTakenCodesAndBadValues()
    {
        ResourceView page = create(null, ResourceType.PAGE, "page");
        create(page, ResourceType.ACTION, "button");
        long crm = fixture.asRoot(() -> applicationService.create(fixture.root, "crm", "CRM", null)).id();

        assertRefused(() -> create(null, ResourceType.ACTION, "loose-button"), AuthzErrorCode.RESOURCE_PLACEMENT_INVALID);
        assertRefused(() -> create(null, ResourceType.PAGE, "page"), AuthzErrorCode.RESOURCE_CODE_TAKEN);
        assertRefused(() -> create(null, ResourceType.PAGE, "bad code"), CommonErrorCode.BAD_REQUEST);
        assertRefused(() -> fixture.asRoot(() -> service.create(fixture.root, crm, page.id(), ResourceType.ACTION, "x",
                CatalogTestData.details("X"))), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> service.create(fixture.root, 42, null, ResourceType.PAGE, "x",
                CatalogTestData.details("X"))), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> service.create(fixture.root, console, 42L, ResourceType.PAGE, "x",
                CatalogTestData.details("X"))), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> service.update(fixture.root, page.id(), "button", CatalogTestData.details("P"))),
                AuthzErrorCode.RESOURCE_CODE_TAKEN);
        assertRefused(() -> fixture.asRoot(() -> service.update(fixture.root, page.id(), "page",
                new ResourceDetails("P", null, "no-slash", true, true, DenyMode.HIDE))), CommonErrorCode.BAD_REQUEST);
        ResourceView crmModule = fixture.asRoot(() -> service.create(fixture.root, crm, null, ResourceType.MODULE, "sales",
                CatalogTestData.details("Sales")));
        assertRefused(() -> fixture.asRoot(() -> service.move(fixture.root, crmModule.id(), page.id(), 0)),
                CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.asRoot(() -> {
            service.delete(fixture.root, page.id());
            return null;
        }), AuthzErrorCode.RESOURCE_NOT_EMPTY);
    }

    @Test
    void builtInResourcesKeepTheirCodeAndCannotBeDeleted()
    {
        ResourceView page = create(null, ResourceType.PAGE, "page");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                resources.findById(page.id()).orElseThrow().markBuiltin());

        assertRefused(() -> fixture.asRoot(() -> service.update(fixture.root, page.id(), "renamed", CatalogTestData.details("P"))),
                AuthzErrorCode.RESOURCE_PROTECTED);
        assertThat(fixture.asRoot(() -> service.update(fixture.root, page.id(), " page ", CatalogTestData.details("Renamed")))
                .details().name()).isEqualTo("Renamed");
        assertRefused(() -> fixture.asRoot(() -> {
            service.delete(fixture.root, page.id());
            return null;
        }), AuthzErrorCode.RESOURCE_PROTECTED);
        assertThat(fixture.asRoot(() -> service.tree(fixture.root, console))).singleElement()
                .satisfies(resource -> assertThat(resource.builtin()).isTrue());
    }

    @Test
    void tenantAdministratorsReadAndOthersAreTurnedAway()
    {
        create(null, ResourceType.MODULE, "system");

        assertThat(fixture.inTenant(() -> service.tree(fixture.boss, console))).hasSize(1);
        assertRefused(() -> fixture.asRoot(() -> service.tree(fixture.root, 42)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> fixture.inTenant(() -> service.create(fixture.boss, console, null, ResourceType.MODULE, "x",
                CatalogTestData.details("X"))), CommonErrorCode.FORBIDDEN);
        assertThat(applications.findByCode(Application.CONSOLE)).isPresent();
    }
}
