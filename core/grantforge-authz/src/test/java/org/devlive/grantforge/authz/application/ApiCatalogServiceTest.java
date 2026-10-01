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
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.authz.domain.EndpointChange;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.common.error.CommonErrorCode;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, ApiCatalogService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApiCatalogServiceTest
{
    @Autowired
    private ApiCatalogService service;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApiEndpointRepository endpoints;

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

    @BeforeEach
    void createAccounts()
    {
        fixture = new CatalogFixture(tenants, accounts, platform);
    }

    @AfterEach
    void deleteRows()
    {
        endpoints.deleteAllInBatch();
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    private static DeclaredEndpoint endpoint(String method, String path, EndpointAccess access, @Nullable String permission)
    {
        return new DeclaredEndpoint(method, path, new ApiEndpoint.Declaration("C#" + method.toLowerCase(Locale.ROOT)
                + path.length(), access, permission));
    }

    private Map<String, ApiEndpointView> catalog()
    {
        return fixture.asRoot(() -> service.list(fixture.root)).stream()
                .collect(Collectors.toMap(view -> view.httpMethod() + " " + view.pathPattern(), view -> view));
    }

    private static ApiEndpointView entry(Map<String, ApiEndpointView> catalog, String route)
    {
        return requireNonNull(catalog.get(route), route);
    }

    @Test
    void synchronizesEndpointsAndCreatesOneApiResourcePerPermission()
    {
        SyncReport first = service.synchronize(List.of(
                endpoint("GET", "/api/v1/users", EndpointAccess.PERMISSION, "system.user.read"),
                endpoint("GET", "/api/v1/users/{id}", EndpointAccess.PERMISSION, "system.user.read"),
                endpoint("DELETE", "/api/v1/users/{id}", EndpointAccess.PERMISSION, "system.user.delete"),
                endpoint("GET", "/api/v1/me", EndpointAccess.AUTHENTICATED, null),
                endpoint("POST", "/api/v1/auth/login", EndpointAccess.PUBLIC, null)));

        assertThat(first).isEqualTo(new SyncReport(5, 5, 0, 0, 2));
        long console = applications.findByCode(Application.CONSOLE).orElseThrow().requireId();
        List<Resource> tree = resources.findTree(console);
        assertThat(tree).extracting(Resource::getCode).containsExactly(ApiCatalogService.API_MODULE, "api:system.user.delete",
                "api:system.user.read");
        assertThat(tree).allSatisfy(resource -> assertThat(resource.isBuiltin()).isTrue());
        assertThat(tree.get(1).getType()).isEqualTo(ResourceType.API);
        Map<String, ApiEndpointView> added = catalog();
        assertThat(entry(added, "GET /api/v1/users/{id}").resourceId()).isEqualTo(tree.get(2).requireId());
        assertThat(entry(added, "GET /api/v1/me").resourceId()).isNull();
        assertThat(added.values()).allSatisfy(view -> assertThat(view.change()).isEqualTo(EndpointChange.ADDED));

        // Review everything, then deploy a version that changes, removes and adds endpoints.
        assertThat(fixture.asRoot(() -> service.review(fixture.root, added.values().stream().map(ApiEndpointView::id).toList())))
                .isEqualTo(5);
        SyncReport second = service.synchronize(List.of(
                endpoint("GET", "/api/v1/users", EndpointAccess.PERMISSION, "system.user.read"),
                endpoint("GET", "/api/v1/users/{id}", EndpointAccess.PERMISSION, "system.user.detail"),
                endpoint("GET", "/api/v1/me", EndpointAccess.AUTHENTICATED, null),
                endpoint("POST", "/api/v1/auth/login", EndpointAccess.PUBLIC, null),
                endpoint("POST", "/api/v1/auth/logout", EndpointAccess.PUBLIC, null)));

        assertThat(second).isEqualTo(new SyncReport(5, 1, 1, 1, 1));
        Map<String, ApiEndpointView> catalog = catalog();
        assertThat(entry(catalog, "GET /api/v1/users/{id}").change()).isEqualTo(EndpointChange.CHANGED);
        assertThat(entry(catalog, "GET /api/v1/users/{id}").permission()).isEqualTo("system.user.detail");
        assertThat(entry(catalog, "DELETE /api/v1/users/{id}").change()).isEqualTo(EndpointChange.REMOVED);
        assertThat(entry(catalog, "DELETE /api/v1/users/{id}").active()).isFalse();
        assertThat(entry(catalog, "POST /api/v1/auth/logout").change()).isEqualTo(EndpointChange.ADDED);
        assertThat(entry(catalog, "GET /api/v1/users").change()).isNull();
        // The permission's resource stays: grants may refer to it.
        assertThat(resources.findByApplicationIdAndCode(console, "api:system.user.delete")).isPresent();

        // Synchronizing the same code again changes nothing.
        assertThat(service.synchronize(List.of(endpoint("GET", "/api/v1/users", EndpointAccess.PERMISSION, "system.user.read"),
                endpoint("GET", "/api/v1/users/{id}", EndpointAccess.PERMISSION, "system.user.detail"),
                endpoint("GET", "/api/v1/me", EndpointAccess.AUTHENTICATED, null),
                endpoint("POST", "/api/v1/auth/login", EndpointAccess.PUBLIC, null),
                endpoint("POST", "/api/v1/auth/logout", EndpointAccess.PUBLIC, null)))).isEqualTo(new SyncReport(5, 0, 0, 0, 0));
    }

    @Test
    void adoptsExistingApiResourcesAndRefusesClashingOnes()
    {
        long console = applicationService.registerConsole();
        resources.save(Resource.create(console, null, ResourceType.API, "api:system.user.read", CatalogTestData.details("Read"), 0));
        resources.save(Resource.create(console, null, ResourceType.MODULE, "api:system.user.delete", CatalogTestData.details("X"), 1));

        service.synchronize(List.of(endpoint("GET", "/api/v1/users", EndpointAccess.PERMISSION, "system.user.read")));
        assertThat(resources.findByApplicationIdAndCode(console, "api:system.user.read").orElseThrow().isBuiltin()).isTrue();
        assertThatThrownBy(() -> service.synchronize(List.of(
                endpoint("DELETE", "/api/v1/users/{id}", EndpointAccess.PERMISSION, "system.user.delete"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("is no API resource");
    }

    @Test
    void refusesDuplicateRoutesAndMalformedPermissions()
    {
        assertThatThrownBy(() -> service.synchronize(List.of(
                endpoint("GET", "/api/v1/users", EndpointAccess.PUBLIC, null),
                endpoint("GET", "/api/v1/users", EndpointAccess.AUTHENTICATED, null),
                endpoint("POST", "/api/v1/users", EndpointAccess.PERMISSION, "Users"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GET /api/v1/users is declared twice")
                .hasMessageContaining("malformed permission Users");
        assertThat(endpoints.count()).isZero();
    }

    @Test
    void onlyPlatformAdministratorsReviewAndReviewsAreAudited()
    {
        service.synchronize(List.of(endpoint("GET", "/api/v1/me", EndpointAccess.AUTHENTICATED, null)));
        long id = endpoints.findAll().get(0).requireId();

        assertThat(fixture.inTenant(() -> service.list(fixture.boss))).hasSize(1);
        assertThatThrownBy(() -> fixture.inTenant(() -> service.review(fixture.boss, List.of(id))))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThat(fixture.asRoot(() -> service.review(fixture.root, List.of(id, 42L)))).isOne();
        assertThat(fixture.asRoot(() -> service.review(fixture.root, List.of(id)))).isZero();
        assertThat(CatalogFixture.trail(events)).containsExactly("API_CHANGES_REVIEWED:" + fixture.root + ":1");
    }
}
