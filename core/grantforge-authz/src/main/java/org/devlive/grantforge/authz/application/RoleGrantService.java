// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DependencyGraph;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * What roles of the bound tenant allow and deny on an application: the explicit grants, and the matrix of what
 * they imply ({@link GrantDerivation}). Changes can be previewed before they are saved. Tenant administrators manage
 * grants until roles grant that themselves; system roles cannot be changed, and platform resources can only be
 * granted in the platform tenant. Every method must be called with the actor's tenant bound.
 */
@Service
public final class RoleGrantService
{
    private final RoleGrantRepository grants;
    private final RoleRepository roles;
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;
    private final ApplicationRepository applications;
    private final TenantRepository tenants;
    private final CatalogAccess access;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param grants grants of the bound tenant
     * @param roles roles of the bound tenant
     * @param resources the resource catalog
     * @param dependencies dependencies, for what grants imply
     * @param applications applications
     * @param tenants tenants, to tell the platform tenant apart
     * @param access tells tenant administrators apart
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param clock the current time, for expiry
     */
    public RoleGrantService(RoleGrantRepository grants, RoleRepository roles, ResourceRepository resources,
            ResourceDependencyRepository dependencies, ApplicationRepository applications, TenantRepository tenants,
            CatalogAccess access, AuditLog audit, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.grants = requireNonNull(grants, "grants");
        this.roles = requireNonNull(roles, "roles");
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.applications = requireNonNull(applications, "applications");
        this.tenants = requireNonNull(tenants, "tenants");
        this.access = requireNonNull(access, "access");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns a role's grants on an application and what they mean.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param applicationId the application
     * @return the matrix
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public GrantMatrix matrix(long actorId, long roleId, long applicationId)
    {
        access.requireTenantAdministrator(actorId);
        return requireNonNull(transactions.execute(status -> {
            Role role = requireRole(roleId);
            requireApplication(applicationId);
            return matrix(role, applicationId, grants.findByRoleId(roleId));
        }));
    }

    /**
     * Shows what a role's grants would mean after some changes, without saving them.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param applicationId the application
     * @param changes the changes
     * @return the matrix as it would be
     * @throws GrantForgeException as {@link #apply}
     */
    public GrantMatrix preview(long actorId, long roleId, long applicationId, List<GrantChange> changes)
    {
        access.requireTenantAdministrator(actorId);
        return requireNonNull(transactions.execute(status -> {
            Role role = requireChangeable(roleId);
            requireApplication(applicationId);
            Map<Long, RoleGrant> after = changed(actorId, role, applicationId, grants.findByRoleId(roleId), changes);
            return matrix(role, applicationId, after.values());
        }));
    }

    /**
     * Changes a role's grants: allows, denies or takes back grants on resources of one application.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param applicationId the application the resources belong to
     * @param changes the changes; at most one per resource (the last wins)
     * @return the matrix after the changes
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown role, application or resource, {@link AuthzErrorCode#ROLE_PROTECTED} for system roles,
     *         {@link AuthzErrorCode#GRANT_TYPE_UNSUPPORTED} or {@link AuthzErrorCode#GRANT_NOT_ALLOWED}
     */
    public GrantMatrix apply(long actorId, long roleId, long applicationId, List<GrantChange> changes)
    {
        access.requireTenantAdministrator(actorId);
        GrantMatrix matrix;
        try {
            matrix = requireNonNull(transactions.execute(status -> {
                Role role = requireChangeable(roleId);
                requireApplication(applicationId);
                List<RoleGrant> before = grants.findByRoleId(roleId);
                Map<Long, RoleGrant> after = changed(actorId, role, applicationId, before, changes);
                Set<Long> touched = changes.stream().map(GrantChange::resourceId).collect(Collectors.toSet());
                for (RoleGrant stored : before) {
                    RoleGrant wanted = after.get(stored.getResourceId());
                    if (wanted == null) {
                        grants.delete(stored);
                    }
                    else if (touched.contains(stored.getResourceId())) {
                        // Update the stored row in place rather than inserting the unsaved replacement.
                        stored.change(wanted.getEffect(), wanted.getExpiresAt(), actorId);
                        after.put(stored.getResourceId(), stored);
                    }
                }
                grants.flush();
                grants.saveAll(after.values());
                grants.flush();
                return matrix(role, applicationId, after.values());
            }));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "grants changed concurrently", race);
        }
        audit.record(new AuditRecord(AuditAction.ROLE_GRANTS_CHANGED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(),
                actorId, null, Long.toString(roleId), Integer.toString(changes.size())));
        return matrix;
    }

    /**
     * Applies changes to the grants, by resource, without changing the stored ones: changed resources get new,
     * unsaved grants. Checks every changed resource.
     */
    private Map<Long, RoleGrant> changed(long actorId, Role role, long applicationId, Collection<RoleGrant> current,
            List<GrantChange> changes)
    {
        Map<Long, RoleGrant> byResource = new LinkedHashMap<>();
        current.forEach(grant -> byResource.put(grant.getResourceId(), grant));
        boolean platformTenant = tenants.findById(TenantContext.requireTenantId()).map(Tenant::isPlatform).orElse(false);
        Set<Long> platformRoots = platformRoots(applicationId);
        for (GrantChange change : changes) {
            Resource resource = requireResource(applicationId, change.resourceId());
            if (!platformTenant && platformRoots.contains(rootOf(resource))) {
                throw notAllowed(resource);
            }
            if (change.effect() == null) {
                byResource.remove(resource.requireId());
                continue;
            }
            // Never touch the stored grants here: a preview must not save anything.
            byResource.put(resource.requireId(), grantOf(role, resource, change, actorId));
        }
        return byResource;
    }

    private static RoleGrant grantOf(Role role, Resource resource, GrantChange change, long actorId)
    {
        try {
            return RoleGrant.create(role.requireId(), resource, requireNonNull(change.effect()), change.expiresAt(), actorId);
        }
        catch (IllegalArgumentException unsupported) {
            throw new GrantForgeException(AuthzErrorCode.GRANT_TYPE_UNSUPPORTED, String.valueOf(unsupported.getMessage()),
                    unsupported);
        }
    }

    private GrantMatrix matrix(Role role, long applicationId, Collection<RoleGrant> current)
    {
        Instant now = clock.instant();
        List<Resource> tree = resources.findTree(applicationId);
        GrantDerivation derivation = new GrantDerivation(tree, new DependencyGraph(dependencies.findByApplicationId(applicationId)));
        List<RoleGrant> ofApplication = current.stream()
                .filter(grant -> tree.stream().anyMatch(resource -> resource.requireId() == grant.getResourceId())).toList();
        Map<Long, GrantDerivation.ResourceState> states = derivation.derive(ofApplication, systemSubtrees(role, applicationId), now);
        List<GrantMatrix.State> derived = states.entrySet().stream().map(entry -> new GrantMatrix.State(entry.getKey(),
                entry.getValue().state(), entry.getValue().explicit(), entry.getValue().reasons())).toList();
        return new GrantMatrix(role.requireId(), applicationId, role.getType() == RoleType.SYSTEM,
                ofApplication.stream().map(grant -> new GrantMatrix.Grant(grant.getResourceId(), grant.getEffect(), grant.getExpiresAt(),
                        grant.appliesAt(now))).toList(), derived);
    }

    /** The modules a system role allows as a whole, as resource IDs of the console application. */
    private List<Long> systemSubtrees(Role role, long applicationId)
    {
        if (role.getType() != RoleType.SYSTEM || !isConsole(applicationId)) {
            return List.of();
        }
        return SystemRole.byCode(role.getCode()).map(SystemRole::modules).orElse(List.of()).stream()
                .flatMap(code -> resources.findByApplicationIdAndCode(applicationId, code).stream()).map(Resource::requireId).toList();
    }

    /** The console's modules that only the platform tenant may grant. */
    private Set<Long> platformRoots(long applicationId)
    {
        if (!isConsole(applicationId)) {
            return Set.of();
        }
        List<String> tenantModules = SystemRole.TENANT_ADMIN.modules();
        return SystemRole.PLATFORM_ADMIN.modules().stream().filter(code -> !tenantModules.contains(code))
                .flatMap(code -> resources.findByApplicationIdAndCode(applicationId, code).stream())
                .map(Resource::requireId).collect(Collectors.toSet());
    }

    private boolean isConsole(long applicationId)
    {
        return applications.findById(applicationId).map(Application::getCode).filter(Application.CONSOLE::equals).isPresent();
    }

    private static long rootOf(Resource resource)
    {
        String path = resource.getPath();
        return Long.parseLong(path.substring(1, path.indexOf('/', 1)));
    }

    private Resource requireResource(long applicationId, long id)
    {
        return resources.findById(id).filter(found -> found.getApplicationId() == applicationId)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + id));
    }

    private static GrantForgeException notAllowed(Resource resource)
    {
        return new GrantForgeException(AuthzErrorCode.GRANT_NOT_ALLOWED, resource.getCode() + " is a platform resource");
    }

    private Role requireRole(long id)
    {
        return roles.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id));
    }

    private Role requireChangeable(long id)
    {
        Role role = requireRole(id);
        if (role.getType() == RoleType.SYSTEM) {
            throw new GrantForgeException(AuthzErrorCode.ROLE_PROTECTED, "system role " + role.getCode() + " has no grants");
        }
        return role;
    }

    private void requireApplication(long applicationId)
    {
        if (!applications.existsById(applicationId)) {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + applicationId);
        }
    }
}
