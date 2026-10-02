// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
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
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, RoleService.class, SystemRoleProvisioner.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleServiceTest
{
    @Autowired
    private RoleService service;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

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
        provisioner.provision(fixture.tenant, false);
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    private <T> T asBoss(Supplier<T> action)
    {
        return fixture.inTenant(action);
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    @Test
    void tenantAdministratorsManageCustomRolesAndEveryChangeIsAudited()
    {
        RoleView auditors = asBoss(() -> service.create(fixture.boss, " Auditors ", "审计员", "只读"));
        assertThat(auditors.code()).isEqualTo("auditors");
        assertThat(auditors.type()).isEqualTo(RoleType.CUSTOM);

        RoleView renamed = asBoss(() -> service.update(fixture.boss, auditors.id(), "auditors", "审计", " "));
        assertThat(renamed.name()).isEqualTo("审计");
        assertThat(renamed.description()).isNull();
        assertThat(asBoss(() -> service.enable(fixture.boss, auditors.id(), false)).enabled()).isFalse();
        assertThat(asBoss(() -> service.enable(fixture.boss, auditors.id(), true)).enabled()).isTrue();
        RoleView copy = asBoss(() -> service.copy(fixture.boss, auditors.id(), "auditors-2", "审计（副本）"));
        assertThat(copy.code()).isEqualTo("auditors-2");
        assertThat(asBoss(() -> service.find(fixture.boss, copy.id())).name()).isEqualTo("审计（副本）");
        assertThat(asBoss(() -> service.list(fixture.boss, null))).extracting(RoleView::code)
                .containsExactly("tenant-admin", "auditors", "auditors-2");
        assertThat(asBoss(() -> service.list(fixture.boss, " 副本 "))).extracting(RoleView::code).containsExactly("auditors-2");
        asBoss(() -> {
            service.delete(fixture.boss, copy.id());
            return null;
        });

        assertThat(CatalogFixture.trail(events)).containsExactly(
                "ROLE_CREATED:" + fixture.boss + ":auditors",
                "ROLE_UPDATED:" + fixture.boss + ":auditors",
                "ROLE_DISABLED:" + fixture.boss + ":auditors",
                "ROLE_ENABLED:" + fixture.boss + ":auditors",
                "ROLE_COPIED:" + fixture.boss + ":" + copy.id(),
                "ROLE_DELETED:" + fixture.boss + ":auditors-2");
    }

    @Test
    void systemRolesCanOnlyBeCopied()
    {
        long admin = asBoss(() -> service.list(fixture.boss, "tenant-admin")).get(0).id();

        assertRefused(() -> asBoss(() -> service.update(fixture.boss, admin, "x", "X", null)), AuthzErrorCode.ROLE_PROTECTED);
        assertRefused(() -> asBoss(() -> service.enable(fixture.boss, admin, false)), AuthzErrorCode.ROLE_PROTECTED);
        assertRefused(() -> asBoss(() -> {
            service.delete(fixture.boss, admin);
            return null;
        }), AuthzErrorCode.ROLE_PROTECTED);
        RoleView copy = asBoss(() -> service.copy(fixture.boss, admin, "helpers", "Helpers"));
        assertThat(copy.type()).isEqualTo(RoleType.CUSTOM);
    }

    @Test
    void refusesTakenCodesBadValuesUnknownRolesAndOtherAccounts()
    {
        RoleView auditors = asBoss(() -> service.create(fixture.boss, "auditors", "Auditors", null));
        asBoss(() -> service.create(fixture.boss, "buyers", "Buyers", null));

        assertRefused(() -> asBoss(() -> service.create(fixture.boss, "AUDITORS", "Again", null)), AuthzErrorCode.ROLE_CODE_TAKEN);
        assertRefused(() -> asBoss(() -> service.update(fixture.boss, auditors.id(), "buyers", "X", null)),
                AuthzErrorCode.ROLE_CODE_TAKEN);
        assertRefused(() -> asBoss(() -> service.copy(fixture.boss, auditors.id(), "buyers", "X")), AuthzErrorCode.ROLE_CODE_TAKEN);
        assertRefused(() -> asBoss(() -> service.create(fixture.boss, "bad code", "X", null)), CommonErrorCode.BAD_REQUEST);
        assertRefused(() -> asBoss(() -> service.update(fixture.boss, auditors.id(), "auditors", " ", null)),
                CommonErrorCode.BAD_REQUEST);
        assertRefused(() -> asBoss(() -> service.find(fixture.boss, 42)), CommonErrorCode.NOT_FOUND);
        // Roles of another tenant do not exist for this one.
        assertRefused(() -> fixture.asRoot(() -> service.find(fixture.root, auditors.id())), CommonErrorCode.NOT_FOUND);
    }
}
