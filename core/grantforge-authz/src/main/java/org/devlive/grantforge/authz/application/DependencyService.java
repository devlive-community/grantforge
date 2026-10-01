// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DependencyGraph;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * What menus, pages, tabs and buttons need to work, such as the APIs a button calls. Administrators read the
 * dependencies; platform administrators change them (see {@link CatalogAccess}). Every method must be called with
 * the actor's tenant bound.
 */
@Service
public final class DependencyService
{
    private final ResourceDependencyRepository dependencies;
    private final ResourceRepository resources;
    private final ApplicationRepository applications;
    private final CatalogAccess access;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param dependencies dependencies
     * @param resources resources
     * @param applications applications, to check that one exists
     * @param access who may read and change the catalog
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public DependencyService(ResourceDependencyRepository dependencies, ResourceRepository resources,
            ApplicationRepository applications, CatalogAccess access, AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.resources = requireNonNull(resources, "resources");
        this.applications = requireNonNull(applications, "applications");
        this.access = requireNonNull(access, "access");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Returns what a resource needs and what needs it.
     *
     * @param actorId the account asking
     * @param resourceId the resource
     * @return the dependencies, each list by creation
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public ResourceDependencies of(long actorId, long resourceId)
    {
        access.requireReader(actorId);
        return requireNonNull(transactions.execute(status -> {
            require(resourceId);
            return new ResourceDependencies(views(dependencies.findByResourceId(resourceId)),
                    views(dependencies.findByDependsOnId(resourceId)));
        }));
    }

    /**
     * Returns every dependency of an application, for drawing them.
     *
     * @param actorId the account asking
     * @param applicationId the application
     * @return the dependencies
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public List<DependencyView> graph(long actorId, long applicationId)
    {
        access.requireReader(actorId);
        return requireNonNull(transactions.execute(status -> {
            if (!applications.existsById(applicationId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + applicationId);
            }
            return views(dependencies.findByApplicationId(applicationId));
        }));
    }

    /**
     * Makes a resource depend on another of the same application.
     *
     * @param actorId the account asking
     * @param resourceId the resource that needs the other
     * @param dependsOnId the resource it needs
     * @param kind how strongly
     * @return the dependency
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#DEPENDENCY_INVALID}, {@link AuthzErrorCode#DEPENDENCY_EXISTS} or
     *         {@link AuthzErrorCode#DEPENDENCY_CYCLE}
     */
    public DependencyView add(long actorId, long resourceId, long dependsOnId, DependencyKind kind)
    {
        requireNonNull(kind, "kind");
        ResourceDependency dependency = write(actorId, () -> {
            Resource resource = require(resourceId);
            Resource target = require(dependsOnId);
            ResourceDependency created;
            try {
                created = ResourceDependency.create(resource, target, kind, DependencySource.MANUAL);
            }
            catch (IllegalArgumentException invalid) {
                throw new GrantForgeException(AuthzErrorCode.DEPENDENCY_INVALID, String.valueOf(invalid.getMessage()), invalid);
            }
            if (dependencies.findByResourceIdAndDependsOnId(resourceId, dependsOnId).isPresent()) {
                throw new GrantForgeException(AuthzErrorCode.DEPENDENCY_EXISTS, resourceId + " already depends on " + dependsOnId);
            }
            if (new DependencyGraph(dependencies.findByApplicationId(resource.getApplicationId())).wouldCycle(resourceId,
                    dependsOnId)) {
                throw new GrantForgeException(AuthzErrorCode.DEPENDENCY_CYCLE, dependsOnId + " already depends on " + resourceId);
            }
            return dependencies.saveAndFlush(created);
        });
        record(AuditAction.RESOURCE_DEPENDENCY_ADDED, actorId, dependency);
        return DependencyView.from(dependency);
    }

    /**
     * Makes a dependency required or optional.
     *
     * @param actorId the account asking
     * @param id the dependency
     * @param kind the new kind
     * @return the dependency
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public DependencyView changeKind(long actorId, long id, DependencyKind kind)
    {
        requireNonNull(kind, "kind");
        ResourceDependency dependency = write(actorId, () -> {
            ResourceDependency found = requireDependency(id);
            found.changeKind(kind);
            return dependencies.saveAndFlush(found);
        });
        record(AuditAction.RESOURCE_DEPENDENCY_CHANGED, actorId, dependency);
        return DependencyView.from(dependency);
    }

    /**
     * Removes a dependency an administrator added.
     *
     * @param actorId the account asking
     * @param id the dependency
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link AuthzErrorCode#DEPENDENCY_DECLARED}
     */
    public void remove(long actorId, long id)
    {
        ResourceDependency dependency = write(actorId, () -> {
            ResourceDependency found = requireDependency(id);
            if (found.getSource() == DependencySource.DECLARED) {
                throw new GrantForgeException(AuthzErrorCode.DEPENDENCY_DECLARED, "dependency " + id + " is declared");
            }
            dependencies.delete(found);
            return found;
        });
        record(AuditAction.RESOURCE_DEPENDENCY_REMOVED, actorId, dependency);
    }

    private ResourceDependency write(long actorId, Supplier<ResourceDependency> change)
    {
        access.requireEditor(actorId);
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "dependency changed concurrently", race);
        }
    }

    private Resource require(long id)
    {
        return resources.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + id));
    }

    private ResourceDependency requireDependency(long id)
    {
        return dependencies.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no dependency " + id));
    }

    private static List<DependencyView> views(List<ResourceDependency> rows)
    {
        return rows.stream().sorted(Comparator.comparing(ResourceDependency::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ResourceDependency::requireId))
                .map(DependencyView::from).toList();
    }

    private void record(AuditAction action, long actorId, ResourceDependency dependency)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(dependency.getResourceId()), Long.toString(dependency.getDependsOnId())));
    }
}
