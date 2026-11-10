// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/**
 * The catalog's tenant boundary. The resource catalog is shared by all tenants, so only accounts of the platform
 * tenant change it; who may read or change it beyond that is a matter of permissions, which the API checks. Must be
 * called with the actor's tenant bound.
 */
@Component
public final class CatalogAccess
{
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final TransactionTemplate transactions;
    private final ApplicationRepository applications;
    private final ObjectProvider<AuthorizationEvaluator> authorization;
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;

    /**
     * Creates the check.
     *
     * @param accounts user accounts of the bound tenant
     * @param tenants tenants, to tell the platform tenant apart
     * @param applications the shared application catalog
     * @param resources resources in the catalog
     * @param dependencies dependencies in the catalog
     * @param authorization evaluates the actor's active roles
     * @param transactionManager opens transactions
     */
    public CatalogAccess(UserAccountRepository accounts, TenantRepository tenants, ApplicationRepository applications,
            ResourceRepository resources, ResourceDependencyRepository dependencies,
            ObjectProvider<AuthorizationEvaluator> authorization, PlatformTransactionManager transactionManager)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.tenants = requireNonNull(tenants, "tenants");
        this.applications = requireNonNull(applications, "applications");
        this.authorization = requireNonNull(authorization, "authorization");
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Requires an account of the platform tenant, the only tenant that changes the shared catalog.
     *
     * @param actorId the account asking
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} otherwise
     */
    public void requireEditor(long actorId)
    {
        boolean platform = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::getTenantId).flatMap(tenants::findById).map(Tenant::isPlatform).orElse(false)));
        if (!platform) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " is not of the platform tenant");
        }
    }

    /**
     * Returns whether the actor's application administrator role restricts its catalog access.
     * @param actorId the account
     * @return whether application scoping applies
     */
    public boolean scoped(long actorId)
    {
        AuthorizationEvaluator evaluator = authorization.getIfAvailable();
        if (evaluator == null) {
            return false;
        }
        var roles = evaluator.snapshot(actorId).roles();
        return roles.contains(SystemRole.APPLICATION_ADMIN.code()) && !roles.contains(SystemRole.PLATFORM_ADMIN.code());
    }

    /**
     * Restricts application administrators to applications explicitly assigned to them.
     * @param actorId the account
     * @param applicationId the application
     * @throws GrantForgeException if the application is not assigned
     */
    public void requireApplication(long actorId, long applicationId)
    {
        if (scoped(actorId)) {
            boolean allowed = Boolean.TRUE.equals(transactions.execute(status -> applications.findById(applicationId)
                    .filter(app -> !app.isBuiltin()).map(Application::getAdministratorId)
                    .filter(id -> id == actorId).isPresent()));
            if (!allowed) {
                throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "application not assigned to account " + actorId);
            }
        }
    }

    /**
     * Requires a platform administrator for application lifecycle operations.
     * @param actorId the account
     * @throws GrantForgeException if the account is not a platform administrator
     */
    public void requirePlatformManager(long actorId)
    {
        requireEditor(actorId);
        boolean system = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::isSystemAccount).orElse(false)));
        AuthorizationEvaluator evaluator = authorization.getIfAvailable();
        if (!system && (evaluator == null || !evaluator.snapshot(actorId).roles().contains(SystemRole.PLATFORM_ADMIN.code()))) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "platform administrator required");
        }
    }

    /**
     * Checks the application owning a resource.
     * @param actorId the account
     * @param resourceId the resource
     */
    public void requireResource(long actorId, long resourceId)
    {
        if (scoped(actorId)) {
            long app = requireNonNull(transactions.execute(status -> resources.findById(resourceId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "resource not found")).getApplicationId()));
            requireApplication(actorId, app);
        }
    }

    /**
     * Checks the application owning a resource dependency.
     * @param actorId the account
     * @param dependencyId the dependency
     */
    public void requireDependency(long actorId, long dependencyId)
    {
        if (scoped(actorId)) {
            long app = requireNonNull(transactions.execute(status -> dependencies.findById(dependencyId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "dependency not found")).getApplicationId()));
            requireApplication(actorId, app);
        }
    }
}
