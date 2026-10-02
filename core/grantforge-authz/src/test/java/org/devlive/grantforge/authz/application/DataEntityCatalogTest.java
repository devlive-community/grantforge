// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
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

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, DataEntityCatalog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataEntityCatalogTest
{
    @Autowired
    private DataEntityCatalog catalog;

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

    private static SecuredEntityDefinition entity(String code, String name)
    {
        return new SecuredEntityDefinition(code, name, Object.class, List.of(), null, null, false, true, null);
    }

    @Test
    void keepsOneDataEntityResourcePerSecuredEntity()
    {
        assertThat(catalog.synchronize(List.of(entity("user", "Users"), entity("group", "Groups")))).isEqualTo(2);
        long console = applicationService.registerConsole();
        Resource module = resources.findByApplicationIdAndCode(console, DataEntityCatalog.MODULE).orElseThrow();
        assertThat(module.getType()).isEqualTo(ResourceType.MODULE);
        assertThat(module.isBuiltin()).isTrue();
        Resource users = resources.findByApplicationIdAndCode(console, "entity:user").orElseThrow();
        assertThat(users).extracting(Resource::getType, Resource::getParentId, Resource::isBuiltin)
                .containsExactly(ResourceType.DATA_ENTITY, module.getId(), true);
        assertThat(users.getDetails().name()).isEqualTo("Users");

        // Renamed entities keep their resource; vanished ones stay for the permissions that refer to them.
        assertThat(catalog.synchronize(List.of(entity("user", "Accounts")))).isZero();
        assertThat(resources.findByApplicationIdAndCode(console, "entity:user").orElseThrow().getDetails().name()).isEqualTo("Accounts");
        assertThat(resources.findByApplicationIdAndCode(console, "entity:group")).isPresent();
    }

    @Test
    void refusesCodesTakenByOtherResources()
    {
        long console = applicationService.registerConsole();
        resources.save(Resource.create(console, null, ResourceType.MODULE, "entity:user", new ResourceDetails("Clash", null, null, true,
                true, DenyMode.HIDE), 0));
        assertThatThrownBy(() -> catalog.synchronize(List.of(entity("user", "Users")))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entity:user");
    }
}
