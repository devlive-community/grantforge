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
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The resource tree of each application. Administrators read it; platform administrators change it (see
 * {@link CatalogAccess}). Every method must be called with the actor's tenant bound.
 */
@Service
public final class ResourceService
{
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;
    private final RoleGrantRepository grants;
    private final ApplicationRepository applications;
    private final CatalogAccess access;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final TransactionTemplate everyTenant;

    /**
     * Creates the service.
     *
     * @param resources resources
     * @param dependencies dependencies, which keep needed resources from being deleted
     * @param grants grants of every tenant, which keep granted resources from being deleted
     * @param applications applications, to check that one exists
     * @param access who may read and change the catalog
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public ResourceService(ResourceRepository resources, ResourceDependencyRepository dependencies, RoleGrantRepository grants,
            ApplicationRepository applications, CatalogAccess access, AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.grants = requireNonNull(grants, "grants");
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.applications = requireNonNull(applications, "applications");
        this.access = requireNonNull(access, "access");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.everyTenant = new TransactionTemplate(transactionManager);
        everyTenant.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        everyTenant.setReadOnly(true);
    }

    /**
     * Returns an application's whole tree, parents before children and siblings in order.
     *
     * @param actorId the account asking
     * @param applicationId the application
     * @return the resources
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<ResourceView> tree(long actorId, long applicationId)
    {
        return requireNonNull(transactions.execute(status -> {
            requireApplication(applicationId);
            return resources.findTree(applicationId).stream().map(ResourceView::from).toList();
        }));
    }

    /**
     * Adds a resource as the last child of a parent, or as the last top-level resource of an application.
     *
     * @param actorId the account asking
     * @param applicationId the application
     * @param parentId the parent, or {@code null} for the top level
     * @param type what the resource stands for
     * @param code the code, unique in the application
     * @param details the settings
     * @return the resource
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#RESOURCE_CODE_TAKEN}, {@link AuthzErrorCode#RESOURCE_PLACEMENT_INVALID},
     *         {@link AuthzErrorCode#RESOURCE_TOO_DEEP} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public ResourceView create(long actorId, long applicationId, @Nullable Long parentId, ResourceType type,
            @Nullable String code, ResourceDetails details)
    {
        requireNonNull(type, "type");
        Resource resource = write(actorId, () -> {
            requireApplication(applicationId);
            Resource parent = parentId == null ? null : require(parentId);
            if (parent != null && parent.getApplicationId() != applicationId) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + parentId + " in the application");
            }
            if (!type.allowsParent(parent == null ? null : parent.getType())) {
                throw placement(type, parent);
            }
            if (parent != null && parent.getDepth() >= Resource.MAX_DEPTH) {
                throw tooDeep();
            }
            Resource created = Catalog.valid(() -> Resource.create(applicationId, parent, type, String.valueOf(code), details,
                    resources.findChildren(applicationId, parentId).size()));
            requireFreeCode(applicationId, created.getCode(), null);
            return resources.saveAndFlush(created);
        });
        record(AuditAction.RESOURCE_CREATED, actorId, resource);
        return ResourceView.from(resource);
    }

    /**
     * Changes the code and settings of a resource. Built-in resources keep their code.
     *
     * @param actorId the account asking
     * @param id the resource
     * @param code the new code
     * @param details the new settings
     * @return the resource
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#RESOURCE_CODE_TAKEN}, {@link AuthzErrorCode#RESOURCE_PROTECTED} or
     *         {@link CommonErrorCode#BAD_REQUEST}
     */
    public ResourceView update(long actorId, long id, @Nullable String code, ResourceDetails details)
    {
        Resource resource = write(actorId, () -> {
            Resource found = require(id);
            String newCode = String.valueOf(code).strip();
            if (!newCode.equals(found.getCode())) {
                if (found.isBuiltin()) {
                    throw new GrantForgeException(AuthzErrorCode.RESOURCE_PROTECTED, "resource " + id + " is built in");
                }
                requireFreeCode(found.getApplicationId(), newCode, id);
            }
            Catalog.valid(() -> {
                found.recode(newCode);
                found.update(details);
                return found;
            });
            return resources.saveAndFlush(found);
        });
        record(AuditAction.RESOURCE_UPDATED, actorId, resource);
        return ResourceView.from(resource);
    }

    /**
     * Moves a resource, with everything below it, under another parent of the same application (or to the top
     * level) at a position among the new siblings.
     *
     * @param actorId the account asking
     * @param id the resource
     * @param parentId the new parent, or {@code null} for the top level
     * @param position the 0-based position among the new siblings; clamped to the valid range
     * @return the resource
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#RESOURCE_PLACEMENT_INVALID}, {@link AuthzErrorCode#RESOURCE_MOVE_CYCLE} or
     *         {@link AuthzErrorCode#RESOURCE_TOO_DEEP}
     */
    public ResourceView move(long actorId, long id, @Nullable Long parentId, int position)
    {
        Resource resource = write(actorId, () -> {
            Resource moving = require(id);
            long applicationId = moving.getApplicationId();
            Resource parent = parentId == null ? null : require(parentId);
            if (parent != null && parent.getApplicationId() != applicationId) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + parentId + " in the application");
            }
            if (!Objects.equals(moving.getParentId(), parentId)) {
                reparent(moving, parent);
            }
            // The bulk update cleared the persistence context; work on fresh copies of the new siblings.
            List<Resource> siblings = new ArrayList<>(resources.findChildren(applicationId, parentId));
            Resource moved = siblings.stream().filter(sibling -> sibling.requireId() == id).findFirst().orElseThrow();
            siblings.remove(moved);
            siblings.add(Math.max(0, Math.min(position, siblings.size())), moved);
            for (int i = 0; i < siblings.size(); i++) {
                siblings.get(i).placeAt(i);
            }
            return moved;
        });
        record(AuditAction.RESOURCE_MOVED, actorId, resource);
        return ResourceView.from(resource);
    }

    /**
     * Deletes a resource that has no children and is not built in.
     *
     * @param actorId the account asking
     * @param id the resource
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#RESOURCE_PROTECTED}, {@link AuthzErrorCode#RESOURCE_NOT_EMPTY} or
     *         {@link AuthzErrorCode#RESOURCE_IN_USE} or {@link AuthzErrorCode#RESOURCE_GRANTED}; its own dependencies are
     *         deleted with it
     */
    public void delete(long actorId, long id)
    {
        Resource resource = write(actorId, () -> {
            Resource found = require(id);
            if (found.isBuiltin()) {
                throw new GrantForgeException(AuthzErrorCode.RESOURCE_PROTECTED, "resource " + id + " is built in");
            }
            if (resources.existsByParentId(id)) {
                throw new GrantForgeException(AuthzErrorCode.RESOURCE_NOT_EMPTY, "resource " + id + " has children");
            }
            int dependents = dependencies.findByDependsOnId(id).size();
            if (dependents > 0) {
                throw new GrantForgeException(AuthzErrorCode.RESOURCE_IN_USE, "resource " + id + " is needed", dependents);
            }
            // Grants belong to tenants; any tenant's grant keeps the resource. The open session is filtered to the
            // actor's tenant, so the count runs in a session of its own without the filter.
            long granted = requireNonNull(TenantContext.callAsSystem(() -> everyTenant.execute(status -> grants.countByResourceId(id))));
            if (granted > 0) {
                throw new GrantForgeException(AuthzErrorCode.RESOURCE_GRANTED, "resource " + id + " is granted", granted);
            }
            dependencies.deleteAll(dependencies.findByResourceId(id));
            resources.delete(found);
            return found;
        });
        record(AuditAction.RESOURCE_DELETED, actorId, resource);
    }

    private void reparent(Resource moving, @Nullable Resource parent)
    {
        // Moving below itself or a descendant would detach the subtree into a cycle.
        if (parent != null && moving.contains(parent)) {
            throw new GrantForgeException(AuthzErrorCode.RESOURCE_MOVE_CYCLE, "cannot move " + moving.requireId()
                    + " below " + parent.requireId());
        }
        if (!moving.canMoveBelow(parent)) {
            throw placement(moving.getType(), parent);
        }
        String oldPrefix = moving.getPath();
        String newPrefix = (parent == null ? "/" : parent.getPath()) + moving.requireId() + "/";
        int shift = (parent == null ? 0 : parent.getDepth() + 1) - moving.getDepth();
        if (resources.maxDepthBelow(oldPrefix + "%") + shift > Resource.MAX_DEPTH) {
            throw tooDeep();
        }
        resources.reparent(moving.requireId(), parent == null ? null : parent.requireId());
        resources.moveSubtree(oldPrefix, oldPrefix + "%", newPrefix, oldPrefix.length() + 1, shift);
    }

    private Resource write(long actorId, Supplier<Resource> change)
    {
        access.requireEditor(actorId);
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "resource changed concurrently", race);
        }
    }

    private void requireApplication(long applicationId)
    {
        if (!applications.existsById(applicationId)) {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + applicationId);
        }
    }

    private void requireFreeCode(long applicationId, String code, @Nullable Long except)
    {
        resources.findByApplicationIdAndCode(applicationId, code).filter(other -> !Objects.equals(other.getId(), except))
                .ifPresent(other -> {
                    throw new GrantForgeException(AuthzErrorCode.RESOURCE_CODE_TAKEN, "resource code taken", code);
                });
    }

    private Resource require(long id)
    {
        return resources.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + id));
    }

    private static GrantForgeException placement(ResourceType type, @Nullable Resource parent)
    {
        return new GrantForgeException(AuthzErrorCode.RESOURCE_PLACEMENT_INVALID, type + " cannot be placed below "
                + (parent == null ? "the top level" : parent.getType()));
    }

    private static GrantForgeException tooDeep()
    {
        return new GrantForgeException(AuthzErrorCode.RESOURCE_TOO_DEEP, "too deep", Resource.MAX_DEPTH + 1);
    }

    private void record(AuditAction action, long actorId, Resource resource)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(resource.requireId()), resource.getCode()));
    }
}
