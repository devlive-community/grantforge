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
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationServiceTest
{
    @Autowired
    private ApplicationService service;

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
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    @Test
    void platformAdministratorsMaintainApplicationsAndEveryChangeIsAudited()
    {
        long console = service.registerConsole();
        ApplicationView crm = fixture.asRoot(() -> service.create(fixture.root, " CRM ", "Customers", " "));
        resources.save(Resource.create(console, null, ResourceType.MODULE, "system", CatalogTestData.details("System"), 0));

        assertThat(crm.code()).isEqualTo("crm");
        assertThat(crm.description()).isNull();
        assertThat(crm.builtin()).isFalse();
        assertThat(fixture.asRoot(() -> service.list(fixture.root))).extracting(ApplicationView::code, ApplicationView::resources)
                .containsExactly(tuple(Application.CONSOLE, 1L),
                        tuple("crm", 0L));

        ApplicationView renamed = fixture.asRoot(() -> service.update(fixture.root, crm.id(), "CRM", "Sales"));
        assertThat(renamed.name()).isEqualTo("CRM");
        assertThat(renamed.description()).isEqualTo("Sales");
        assertThat(fixture.asRoot(() -> service.update(fixture.root, console, "Console", null)).resources()).isOne();
        fixture.asRoot(() -> {
            service.delete(fixture.root, crm.id());
            return null;
        });

        assertThat(applications.findByCode("crm")).isEmpty();
        assertThat(CatalogFixture.trail(events)).containsExactly(
                "APPLICATION_CREATED:" + fixture.root + ":crm",
                "APPLICATION_UPDATED:" + fixture.root + ":crm",
                "APPLICATION_UPDATED:" + fixture.root + ":" + Application.CONSOLE,
                "APPLICATION_DELETED:" + fixture.root + ":crm");
        assertThat(CatalogFixture.events(events)).allSatisfy(event -> assertThat(event.getTenantId()).isEqualTo(fixture.platform));
    }

    @Test
    void theConsoleRegistersOnce()
    {
        long first = service.registerConsole();

        assertThat(service.registerConsole()).isEqualTo(first);
        assertThat(applications.findByCode(Application.CONSOLE)).hasValueSatisfying(application ->
                assertThat(application.isBuiltin()).isTrue());
    }

    @Test
    void tenantAdministratorsReadAndOthersAreTurnedAway()
    {
        service.registerConsole();

        assertThat(fixture.inTenant(() -> service.list(fixture.boss))).hasSize(1);
        assertThatThrownBy(() -> fixture.inTenant(() -> service.list(fixture.member)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> fixture.inTenant(() -> service.create(fixture.boss, "crm", "CRM", null)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThat(events.count()).isZero();
    }

    @Test
    void refusesTakenCodesBadValuesAndProtectedOrNonEmptyApplications()
    {
        long console = service.registerConsole();
        ApplicationView crm = fixture.asRoot(() -> service.create(fixture.root, "crm", "CRM", null));
        resources.save(Resource.create(crm.id(), null, ResourceType.MODULE, "sales", CatalogTestData.details("Sales"), 0));

        assertThatThrownBy(() -> fixture.asRoot(() -> service.create(fixture.root, "CRM", "Again", null)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(AuthzErrorCode.APPLICATION_CODE_TAKEN));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.create(fixture.root, "bad code", "Bad", null)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.update(fixture.root, crm.id(), " ", null)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.update(fixture.root, 42, "CRM", null)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> fixture.asRoot(() -> {
            service.delete(fixture.root, console);
            return null;
        })).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(AuthzErrorCode.APPLICATION_PROTECTED));
        assertThatThrownBy(() -> fixture.asRoot(() -> {
            service.delete(fixture.root, crm.id());
            return null;
        })).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(AuthzErrorCode.APPLICATION_NOT_EMPTY));
    }
}
