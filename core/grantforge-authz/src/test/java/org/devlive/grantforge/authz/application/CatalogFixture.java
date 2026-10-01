// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.TenantService;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.Mockito.when;

/**
 * Accounts for the catalog tests: {@code root} administers the platform, {@code boss} administers another tenant
 * and {@code member} administers nothing.
 */
final class CatalogFixture
{
    final long platform;
    final long tenant;
    final long root;
    final long boss;
    final long member;

    private final TenantRepository tenants;
    private final UserAccountRepository accounts;

    CatalogFixture(TenantRepository tenants, UserAccountRepository accounts, TenantService tenantService)
    {
        this.tenants = tenants;
        this.accounts = accounts;
        platform = tenants.save(Tenant.create("platform", "Platform").markPlatform()).requireId();
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        root = TenantContext.callInTenant(platform, () -> accounts.save(UserAccount.create("root", "h", Instant.EPOCH)
                .markSystemAccount()).requireId());
        boss = TenantContext.callInTenant(tenant, () -> accounts.save(UserAccount.create("boss", "h", Instant.EPOCH)
                .markSystemAccount()).requireId());
        member = TenantContext.callInTenant(tenant, () -> accounts.save(UserAccount.create("member", "h", Instant.EPOCH))
                .requireId());
        when(tenantService.isPlatformAdministrator(root)).thenReturn(true);
    }

    /** Runs an action as {@code root}, in the platform tenant. */
    <T> T asRoot(Supplier<T> action)
    {
        return TenantContext.callInTenant(platform, action::get);
    }

    /** Runs an action as {@code boss} or {@code member}, in their tenant. */
    <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action::get);
    }

    void deleteRows(ResourceRepository resources, ApplicationRepository applications, AuditEventRepository events,
            PlatformTransactionManager transactionManager)
    {
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
        events.deleteAllInBatch();
        TenantContext.callAsSystem(() -> {
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    static List<String> trail(AuditEventRepository events)
    {
        return events.findAll(Sort.by("occurredAt", "id")).stream()
                .map(event -> event.getAction() + ":" + event.getActorId() + ":" + event.getReason()).toList();
    }

    static List<AuditEvent> events(AuditEventRepository events)
    {
        return events.findAll(Sort.by("occurredAt", "id"));
    }

    static ErrorCode errorOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }
}
