// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.identity.application.TenantCreated;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Gives every tenant its system roles ({@link SystemRole}): when a tenant is created, and at start-up for tenants
 * that predate a system role.
 */
@Component
public final class SystemRoleProvisioner
        implements ApplicationRunner
{
    private final RoleRepository roles;
    private final TenantRepository tenants;
    private final TransactionTemplate transactions;

    /**
     * Creates the provisioner.
     *
     * @param roles roles of the bound tenant
     * @param tenants every tenant
     * @param transactionManager opens transactions
     */
    public SystemRoleProvisioner(RoleRepository roles, TenantRepository tenants, PlatformTransactionManager transactionManager)
    {
        this.roles = requireNonNull(roles, "roles");
        this.tenants = requireNonNull(tenants, "tenants");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Gives a new tenant its system roles.
     *
     * @param event the new tenant
     */
    @EventListener
    public void created(TenantCreated event)
    {
        provision(event.tenantId(), event.platform());
    }

    @Override
    public void run(ApplicationArguments args)
    {
        List<Tenant> all = requireNonNull(transactions.execute(status -> tenants.findAll()));
        all.forEach(tenant -> provision(tenant.requireId(), tenant.isPlatform()));
    }

    /**
     * Creates the system roles a tenant lacks.
     *
     * @param tenantId the tenant
     * @param platform whether it is the platform tenant
     * @return how many roles were created
     */
    public int provision(long tenantId, boolean platform)
    {
        return TenantContext.callInTenant(tenantId, () -> {
            try {
                return create(platform);
            }
            catch (DataIntegrityViolationException race) {
                // Another node created them at the same moment; whatever is still missing is created now.
                return create(platform);
            }
        });
    }

    private int create(boolean platform)
    {
        return requireNonNull(transactions.execute(status -> {
            int created = 0;
            for (SystemRole definition : SystemRole.values()) {
                if (definition.belongsTo(platform) && roles.findByCode(definition.code()).isEmpty()) {
                    roles.saveAndFlush(Role.system(definition));
                    created++;
                }
            }
            return created;
        }));
    }
}
