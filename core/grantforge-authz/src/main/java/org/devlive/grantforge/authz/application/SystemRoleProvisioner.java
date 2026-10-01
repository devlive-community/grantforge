// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.identity.application.TenantCreated;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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
 * Gives every tenant its system roles ({@link SystemRole}) and gives them to the tenant's system accounts: when a
 * tenant is created, and at start-up for tenants that predate a system role or its assignments.
 */
@Component
public final class SystemRoleProvisioner
        implements ApplicationRunner
{
    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final TransactionTemplate transactions;

    /**
     * Creates the provisioner.
     *
     * @param roles roles of the bound tenant
     * @param assignments assignments of the bound tenant
     * @param accounts accounts of the bound tenant, for its system accounts
     * @param tenants every tenant
     * @param transactionManager opens transactions
     */
    public SystemRoleProvisioner(RoleRepository roles, RoleAssignmentRepository assignments, UserAccountRepository accounts,
            TenantRepository tenants, PlatformTransactionManager transactionManager)
    {
        this.roles = requireNonNull(roles, "roles");
        this.assignments = requireNonNull(assignments, "assignments");
        this.accounts = requireNonNull(accounts, "accounts");
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
     * Creates the system roles a tenant lacks and gives them to its system accounts that lack them.
     *
     * @param tenantId the tenant
     * @param platform whether it is the platform tenant
     * @return how many roles and assignments were created
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

    private int assign(Role role, List<UserAccount> administrators)
    {
        int created = 0;
        for (UserAccount administrator : administrators) {
            if (assignments.findByRoleIdAndSubjectTypeAndSubjectId(role.requireId(), SubjectType.USER, administrator.requireId())
                    .isEmpty()) {
                assignments.saveAndFlush(RoleAssignment.create(role.requireId(), SubjectType.USER, administrator.requireId(),
                        RoleAssignment.Terms.UNLIMITED));
                created++;
            }
        }
        return created;
    }

    private int create(boolean platform)
    {
        return requireNonNull(transactions.execute(status -> {
            int created = 0;
            List<UserAccount> administrators = accounts.findBySystemAccountTrue();
            for (SystemRole definition : SystemRole.values()) {
                if (!definition.belongsTo(platform)) {
                    continue;
                }
                Role role = roles.findByCode(definition.code()).orElse(null);
                if (role == null) {
                    role = roles.saveAndFlush(Role.system(definition));
                    created++;
                }
                created += assign(role, administrators);
            }
            return created;
        }));
    }
}
