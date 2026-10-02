// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.ApiEndpointRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, CatalogHealthService.class,
        CatalogHealthServiceTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogHealthServiceTest
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
    private CatalogHealthService health;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private ResourceDependencyRepository dependencies;

    @Autowired
    private ApiEndpointRepository endpoints;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleGrantRepository grants;

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
    private long console;

    @BeforeEach
    void createCatalog()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        console = applicationService.registerConsole();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            grants.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        endpoints.deleteAllInBatch();
        dependencies.deleteAllInBatch();
        catalog.deleteRows(resources, applications, events, transactionManager);
    }

    private Resource save(@Nullable Resource parent, ResourceType type, String code, boolean enabled)
    {
        ResourceDetails details = new ResourceDetails(code, null, null, true, enabled, DenyMode.HIDE);
        return resources.save(Resource.create(console, parent, type, code, details, 0));
    }

    private Resource api(Resource module, String code, boolean enabled, boolean inService)
    {
        Resource api = save(module, ResourceType.API, "api:" + code, enabled);
        ApiEndpoint endpoint = ApiEndpoint.discover("GET", "/api/v1/" + code,
                new ApiEndpoint.Declaration("Controller#" + code, EndpointAccess.PERMISSION, code), api.requireId(), NOW);
        if (!inService) {
            endpoint.vanish(NOW);
        }
        endpoints.save(endpoint);
        return api;
    }

    private void needs(Resource dependent, Resource target)
    {
        dependencies.save(ResourceDependency.create(dependent, target, DependencyKind.REQUIRED, DependencySource.MANUAL));
    }

    private static <T> T in(long tenant, Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action::get);
    }

    private void grant(long tenant, long role, Resource resource, @Nullable Instant expiresAt)
    {
        in(tenant, () -> grants.save(RoleGrant.create(role, resource, GrantEffect.ALLOW, expiresAt, 1)));
    }

    @Test
    void findsWhatSilentlyDoesNotWork()
    {
        Resource system = save(null, ResourceType.MODULE, "system", true);
        Resource users = save(system, ResourceType.PAGE, "system.user", true);
        Resource edit = save(users, ResourceType.ACTION, "system.user.btn.edit", true);
        Resource chain = save(users, ResourceType.ACTION, "system.user.btn.chain", true);
        Resource orphan = save(users, ResourceType.ACTION, "system.user.btn.orphan", true);
        Resource dead = save(users, ResourceType.ACTION, "system.user.btn.dead", true);
        Resource hidden = save(users, ResourceType.ACTION, "system.user.btn.hidden", false);
        Resource apis = save(null, ResourceType.MODULE, "api", true);
        Resource update = api(apis, "system.user.update", true, true);
        Resource retired = api(apis, "system.user.retired", true, false);
        Resource unused = api(apis, "system.user.unused", true, true);
        Resource direct = api(apis, "system.user.direct", true, true);
        Resource offline = api(apis, "system.user.offline", false, true);
        needs(edit, update);
        needs(chain, edit);
        needs(dead, retired);
        needs(users, offline);
        long auditors = in(catalog.tenant, () -> roles.save(Role.create("auditors", "Auditors", null)).requireId());
        long ops = in(catalog.platform, () -> roles.save(Role.create("ops", "Ops", null)).requireId());
        grant(catalog.tenant, auditors, hidden, null);
        grant(catalog.tenant, auditors, retired, null);
        grant(catalog.tenant, auditors, edit, NOW);
        grant(catalog.tenant, auditors, direct, null);
        grant(catalog.platform, ops, retired, null);

        HealthReport report = catalog.asRoot(() -> health.check(console));

        assertThat(report.applicationId()).isEqualTo(console);
        assertThat(report.checkedAt()).isEqualTo(NOW);
        assertThat(report.findings()).extracting(finding -> finding.issue() + " " + finding.resourceCode() + " "
                + finding.relatedCode() + " " + finding.tenantCode() + " " + finding.roleCode()).containsExactly(
                "GRANT_ON_DISABLED system.user.btn.hidden null acme auditors",
                "GRANT_ON_RETIRED_API api:system.user.retired null acme auditors",
                "GRANT_ON_RETIRED_API api:system.user.retired null platform ops",
                "GRANT_EXPIRED system.user.btn.edit null acme auditors",
                "ACTION_WITHOUT_API system.user.btn.dead null null null",
                "ACTION_WITHOUT_API system.user.btn.orphan null null null",
                "UNUSED_API api:system.user.unused null null null",
                "DEPENDENCY_ON_DISABLED system.user api:system.user.offline null null",
                "DEPENDENCY_ON_RETIRED_API system.user.btn.dead api:system.user.retired null null");
        assertThat(report.findings().get(7).relatedId()).isEqualTo(offline.requireId());
        assertThat(unused.requireId()).isEqualTo(report.findings().get(6).resourceId());
    }

    @Test
    void aHealthyOrEmptyApplicationHasNoFindingsAndAnUnknownOneIsReported()
    {
        assertThat(catalog.asRoot(() -> health.check(console)).findings()).isEmpty();
        assertThatThrownBy(() -> catalog.asRoot(() -> health.check(-1)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(CatalogTestData.details("x").enabled()).isTrue();
    }
}
