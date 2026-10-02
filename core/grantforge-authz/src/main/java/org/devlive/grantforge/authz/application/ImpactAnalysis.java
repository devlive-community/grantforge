// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Works out what a change would do before it is made: for every role, what it can use before and after, with what
 * it inherits, and which accounts hold the roles whose access changes. Changing a role's grants touches that role
 * and the roles inheriting from it in the bound tenant; changing the catalog (a dependency, a disabled resource)
 * touches roles of every tenant.
 */
@Service
public final class ImpactAnalysis
{
    private final AuthorizationEvaluator evaluator;
    private final RoleRepository roles;
    private final RoleGrantRepository grants;
    private final RoleHolders holders;
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;
    private final TenantRepository tenants;
    private final TransactionTemplate inTenant;
    private final Clock clock;

    /**
     * Creates the analysis.
     *
     * @param evaluator works out what roles can use
     * @param roles roles of the bound tenant
     * @param grants grants of the bound tenant
     * @param holders finds the accounts holding roles
     * @param resources the resource catalog
     * @param dependencies dependencies between resources
     * @param tenants every tenant, for changes of the shared catalog
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public ImpactAnalysis(AuthorizationEvaluator evaluator, RoleRepository roles, RoleGrantRepository grants, RoleHolders holders,
            ResourceRepository resources, ResourceDependencyRepository dependencies, TenantRepository tenants, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.roles = requireNonNull(roles, "roles");
        this.grants = requireNonNull(grants, "grants");
        this.holders = requireNonNull(holders, "holders");
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.tenants = requireNonNull(tenants, "tenants");
        this.inTenant = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        inTenant.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        inTenant.setReadOnly(true);
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Works out what new grants of a role would do, in the bound tenant; within a transaction.
     *
     * @param roleId the role
     * @param applicationId the application the grants belong to
     * @param after the role's grants after the change
     * @return the impact
     */
    ImpactReport ofGrants(long roleId, long applicationId, Collection<RoleGrant> after)
    {
        CatalogView catalog = evaluator.catalog(applicationId);
        Changes changes = new Changes();
        compare(catalog, null, roleId, List.copyOf(after), null, changes);
        return changes.report();
    }

    /**
     * Works out what a new dependency would do to the roles of every tenant.
     *
     * @param resourceId the resource that would need the other
     * @param dependsOnId the resource it would need
     * @param kind how strongly; an optional dependency implies nothing
     * @return the impact
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown resource
     */
    public ImpactReport ofNewDependency(long resourceId, long dependsOnId, DependencyKind kind)
    {
        Resource dependent = requireResource(resourceId);
        Resource target = requireResource(dependsOnId);
        ResourceDependency added;
        try {
            added = ResourceDependency.create(dependent, target, kind, DependencySource.MANUAL);
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
        return ofCatalog(dependent.getApplicationId(), catalog -> {
            List<ResourceDependency> changed = new ArrayList<>(catalog.dependencies());
            changed.add(added);
            return catalog.withDependencies(changed);
        });
    }

    /**
     * Works out what changing the kind of a dependency, or removing it, would do to the roles of every tenant.
     *
     * @param dependencyId the dependency
     * @param kind its new kind, or {@code null} to remove it
     * @return the impact
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown dependency
     */
    public ImpactReport ofDependencyChange(long dependencyId, @Nullable DependencyKind kind)
    {
        ResourceDependency existing = requireNonNull(TenantContext.callAsSystem(() -> inTenant.execute(status ->
                dependencies.findById(dependencyId).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND,
                        "no dependency " + dependencyId)))));
        @Nullable ResourceDependency replacement = kind == null ? null : ResourceDependency.create(
                requireResource(existing.getResourceId()), requireResource(existing.getDependsOnId()), kind, existing.getSource());
        return ofCatalog(existing.getApplicationId(), catalog -> {
            List<ResourceDependency> changed = new ArrayList<>(catalog.dependencies().stream()
                    .filter(dependency -> dependency.requireId() != dependencyId).toList());
            if (replacement != null) {
                changed.add(replacement);
            }
            return catalog.withDependencies(changed);
        });
    }

    /**
     * Works out what enabling or disabling a resource would do to the roles of every tenant.
     *
     * @param resourceId the resource
     * @param enabled whether it would be enabled
     * @return the impact
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown resource
     */
    public ImpactReport ofEnabled(long resourceId, boolean enabled)
    {
        Resource resource = requireResource(resourceId);
        return ofCatalog(resource.getApplicationId(), catalog -> catalog.withEnabled(resourceId, enabled));
    }

    private Resource requireResource(long id)
    {
        return requireNonNull(TenantContext.callAsSystem(() -> inTenant.execute(status -> resources.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + id)))));
    }

    /** Works out what a change of an application's catalog would do to the roles of every tenant. */
    private ImpactReport ofCatalog(long applicationId, UnaryOperator<CatalogView> change)
    {
        CatalogView before = requireNonNull(TenantContext.callAsSystem(() -> inTenant.execute(status ->
                evaluator.catalog(applicationId))));
        CatalogView after = change.apply(before);
        List<Tenant> every = requireNonNull(TenantContext.callAsSystem(() -> inTenant.execute(status -> tenants.findAll())));
        Changes changes = new Changes();
        for (Tenant tenant : every) {
            TenantContext.runInTenant(tenant.requireId(), () -> inTenant.executeWithoutResult(status ->
                    compare(before, after, -1, List.of(), tenant.getCode(), changes)));
        }
        return changes.report();
    }

    /**
     * Compares every enabled role of the bound tenant before and after: with the catalog {@code changed} (if not
     * {@code null}), and with {@code replaced} as the grants of {@code changedRole}.
     */
    private void compare(CatalogView before, @Nullable CatalogView changed, long changedRole, List<RoleGrant> replaced,
            @Nullable String tenantCode, Changes changes)
    {
        Instant now = clock.instant();
        Map<Long, Resource> byId = before.byId();
        List<Role> enabled = roles.findAll().stream().filter(Role::isEnabled).toList();
        Map<Long, List<RoleGrant>> stored = grants.findByRoleIdIn(enabled.stream().map(Role::requireId).toList()).stream()
                .collect(Collectors.groupingBy(RoleGrant::getRoleId));
        List<Long> touched = new ArrayList<>();
        for (Role role : enabled) {
            List<RoleView> expanded = evaluator.inherited(List.of(RoleView.from(role)));
            boolean inheritsChange = expanded.stream().anyMatch(view -> view.id() == changedRole);
            if (changed == null && !inheritsChange) {
                continue;
            }
            CatalogView after = changed == null ? before : changed;
            List<RoleGrant> was = expanded.stream().flatMap(view -> stored.getOrDefault(view.id(), List.of()).stream()).toList();
            List<RoleGrant> will = expanded.stream().flatMap(view -> view.id() == changedRole ? replaced.stream()
                    : stored.getOrDefault(view.id(), List.of()).stream()).toList();
            Set<Long> had = evaluator.usable(expanded, before, was, now).keySet();
            Set<Long> has = evaluator.usable(expanded, after, will, now).keySet();
            if (!had.equals(has)) {
                Set<Long> gained = without(has, had);
                Set<Long> lost = without(had, has);
                changes.roles.add(new ImpactReport.AffectedRole(role.requireId(), tenantCode, role.getCode(), role.getName(),
                        gained.size(), lost.size()));
                gained.forEach(id -> changes.gained.add(codeOf(byId, id)));
                lost.forEach(id -> changes.lost.add(codeOf(byId, id)));
                touched.add(role.requireId());
            }
        }
        if (!touched.isEmpty()) {
            changes.accounts += holders.of(touched, now).size();
        }
    }

    private static Set<Long> without(Set<Long> from, Set<Long> taken)
    {
        Set<Long> left = new HashSet<>(from);
        left.removeAll(taken);
        return left;
    }

    private static String codeOf(Map<Long, Resource> byId, long id)
    {
        Resource resource = byId.get(id);
        return resource == null ? Long.toString(id) : resource.getCode();
    }

    /** What the compared tenants add up to. */
    private static final class Changes
    {
        private final List<ImpactReport.AffectedRole> roles = new ArrayList<>();
        private final Set<String> gained = new TreeSet<>();
        private final Set<String> lost = new TreeSet<>();
        private long accounts;

        ImpactReport report()
        {
            return new ImpactReport(roles, accounts, List.copyOf(gained), List.copyOf(lost));
        }
    }
}
