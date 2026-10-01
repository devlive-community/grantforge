// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.ApiEndpointRepository;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.devlive.grantforge.authz.application.ConsoleManifestTest.entry;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, ApiCatalogService.class,
        ManifestService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ManifestServiceTest
{
    @Autowired
    private ManifestService service;

    @Autowired
    private ApiCatalogService apis;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private ResourceDependencyRepository dependencies;

    @Autowired
    private ApiEndpointRepository endpoints;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PlatformAdministrators platform;

    private long console;

    @BeforeEach
    void registerApis()
    {
        apis.synchronize(List.of(permission("GET", "/api/v1/users", "system.user.read"),
                permission("PUT", "/api/v1/users/{id}", "system.user.update"),
                permission("GET", "/api/v1/org-units", "system.org.read")));
        console = applications.findByCode(Application.CONSOLE).orElseThrow().requireId();
    }

    @AfterEach
    void deleteRows()
    {
        endpoints.deleteAllInBatch();
        dependencies.deleteAllInBatch();
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private static DeclaredEndpoint permission(String method, String path, String code)
    {
        return new DeclaredEndpoint(method, path, new ApiEndpoint.Declaration("C#" + code, EndpointAccess.PERMISSION, code));
    }

    private static ConsoleManifest manifest(List<String> pageApis, List<String> editRequires)
    {
        return new ConsoleManifest(List.of(entry("system", ResourceType.MODULE, List.of(), List.of(),
                new ManifestEntry("system.user", ResourceType.PAGE, "Users", "titles.users", "/admin/users", pageApis, List.of(),
                        List.of(entry("system.user.btn.view", ResourceType.ACTION, List.of("system.user.read"), List.of()),
                                entry("system.user.btn.edit", ResourceType.ACTION, List.of("system.user.update"), editRequires))))));
    }

    private Resource resource(String code)
    {
        return resources.findByApplicationIdAndCode(console, code).orElseThrow();
    }

    private List<String> declared()
    {
        return dependencies.findByApplicationId(console).stream().map(dependency -> resources.findById(dependency.getResourceId())
                .orElseThrow().getCode() + " -> " + resources.findById(dependency.getDependsOnId()).orElseThrow().getCode() + " "
                + dependency.getSource()).sorted().toList();
    }

    @Test
    void createsBuiltInResourcesAndDeclaredDependencies()
    {
        ManifestReport report = service.synchronize(manifest(List.of("system.user.read", "system.org.read"),
                List.of("system.user.btn.view")));

        assertThat(report).isEqualTo(new ManifestReport(4, 4, 0, 5, 0));
        Resource page = resource("system.user");
        assertThat(page.isBuiltin()).isTrue();
        assertThat(page.getNameKey()).isEqualTo("titles.users");
        assertThat(page.getDetails().route()).isEqualTo("/admin/users");
        assertThat(page.getParentId()).isEqualTo(resource("system").requireId());
        assertThat(resource("system.user.btn.edit").getParentId()).isEqualTo(page.requireId());
        assertThat(declared()).containsExactly(
                "system.user -> api:system.org.read DECLARED",
                "system.user -> api:system.user.read DECLARED",
                "system.user.btn.edit -> api:system.user.update DECLARED",
                "system.user.btn.edit -> system.user.btn.view DECLARED",
                "system.user.btn.view -> api:system.user.read DECLARED");

        // The same manifest again changes nothing.
        assertThat(service.synchronize(manifest(List.of("system.user.read", "system.org.read"), List.of("system.user.btn.view"))))
                .isEqualTo(new ManifestReport(4, 0, 0, 0, 0));
    }

    @Test
    void followsTheManifestAndTakesOverManualDependenciesItDeclares()
    {
        service.synchronize(manifest(List.of("system.user.read", "system.org.read"), List.of()));
        // An administrator renames the page and links the edit button to the view button by hand.
        Resource view = resource("system.user.btn.view");
        dependencies.save(ResourceDependency.create(resource("system.user.btn.edit"), view, DependencyKind.OPTIONAL,
                DependencySource.MANUAL));
        dependencies.save(ResourceDependency.create(resource("system.user.btn.view"), resource("api:system.org.read"),
                DependencyKind.OPTIONAL, DependencySource.MANUAL));

        ManifestReport report = service.synchronize(manifest(List.of("system.user.read"), List.of("system.user.btn.view")));

        assertThat(report.dependenciesRemoved()).isEqualTo(1);
        assertThat(report.dependenciesAdded()).isEqualTo(1);
        assertThat(declared()).containsExactly(
                "system.user -> api:system.user.read DECLARED",
                "system.user.btn.edit -> api:system.user.update DECLARED",
                "system.user.btn.edit -> system.user.btn.view DECLARED",
                "system.user.btn.view -> api:system.org.read MANUAL",
                "system.user.btn.view -> api:system.user.read DECLARED");
    }

    @Test
    void adoptsExistingResourcesAndRefreshesTheirNames()
    {
        Resource module = resources.save(Resource.create(console, null, ResourceType.MODULE, "system", CatalogTestData.details("Mine"), 0));

        ManifestReport report = service.synchronize(manifest(List.of(), List.of()));

        assertThat(report.created()).isEqualTo(3);
        assertThat(report.updated()).isEqualTo(1);
        Resource adopted = resources.findById(module.requireId()).orElseThrow();
        assertThat(adopted.isBuiltin()).isTrue();
        assertThat(adopted.getDetails().name()).isEqualTo("system");
    }

    @Test
    void refusesBrokenManifests()
    {
        assertThatThrownBy(() -> service.synchronize(new ConsoleManifest(List.of(entry("a", ResourceType.MODULE, List.of(), List.of()),
                entry("a", ResourceType.MODULE, List.of(), List.of()), entry("b", ResourceType.PAGE, List.of(), List.of("nowhere"))))))
                .hasMessageContaining("a is declared twice").hasMessageContaining("b requires the undeclared resource nowhere");
        assertThatThrownBy(() -> service.synchronize(new ConsoleManifest(List.of(entry("p", ResourceType.PAGE, List.of("system.unknown"),
                List.of()))))).hasMessageContaining("unknown permission system.unknown");
        assertThatThrownBy(() -> service.synchronize(new ConsoleManifest(List.of(entry("loose", ResourceType.ACTION, List.of(), List.of())))))
                .hasMessageContaining("manifest entry loose: ACTION cannot be placed below the top level");
        assertThatThrownBy(() -> service.synchronize(new ConsoleManifest(List.of(entry("api", ResourceType.PAGE, List.of(), List.of())))))
                .hasMessageContaining("exists as MODULE");
        assertThatThrownBy(() -> service.synchronize(new ConsoleManifest(List.of(entry("m", ResourceType.MODULE, List.of(), List.of(),
                entry("p", ResourceType.PAGE, List.of(), List.of("q")), entry("q", ResourceType.PAGE, List.of(), List.of("p")))))))
                .hasMessageContaining("close a cycle");
        assertThatThrownBy(() -> service.synchronize(new ConsoleManifest(List.of(entry("m2", ResourceType.MODULE, List.of(),
                List.of("m2-page"), entry("m2-page", ResourceType.PAGE, List.of(), List.of()))))))
                .hasMessageContaining("MODULE cannot depend on PAGE");
    }
}
