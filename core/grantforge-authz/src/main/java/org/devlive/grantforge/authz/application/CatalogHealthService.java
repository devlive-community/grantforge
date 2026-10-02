// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.ApiEndpointRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The catalog check-up: finds what silently does not work in an application's resources, dependencies and the
 * grants every tenant gave of them, such as a grant of a disabled resource, a button that reaches no API, an
 * API nobody can call, or a dependency on an API the server no longer offers. It changes nothing.
 */
@Service
public final class CatalogHealthService
{
    private final ApplicationRepository applications;
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;
    private final ApiEndpointRepository endpoints;
    private final RoleGrantRepository grants;
    private final RoleRepository roles;
    private final TenantRepository tenants;
    private final TransactionTemplate catalog;
    private final TransactionTemplate everyTenant;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param applications applications
     * @param resources resources
     * @param dependencies dependencies between resources
     * @param endpoints the endpoints the server offers, which tell APIs in service from retired ones
     * @param grants grants, read across tenants
     * @param roles roles, read across tenants
     * @param tenants tenants, to name the tenant of a grant
     * @param transactionManager opens transactions
     * @param clock the current time, for expired grants
     */
    public CatalogHealthService(ApplicationRepository applications, ResourceRepository resources,
            ResourceDependencyRepository dependencies, ApiEndpointRepository endpoints, RoleGrantRepository grants,
            RoleRepository roles, TenantRepository tenants, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.applications = requireNonNull(applications, "applications");
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.endpoints = requireNonNull(endpoints, "endpoints");
        this.grants = requireNonNull(grants, "grants");
        this.roles = requireNonNull(roles, "roles");
        this.tenants = requireNonNull(tenants, "tenants");
        this.catalog = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        catalog.setReadOnly(true);
        this.everyTenant = new TransactionTemplate(transactionManager);
        everyTenant.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        everyTenant.setReadOnly(true);
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Checks an application.
     *
     * @param applicationId the application
     * @return the findings, by issue and resource code
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown application
     */
    public HealthReport check(long applicationId)
    {
        Instant now = clock.instant();
        Catalog found = requireNonNull(catalog.execute(status -> {
            if (applications.findById(applicationId).isEmpty()) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + applicationId);
            }
            Set<Long> inService = endpoints.findAll().stream().filter(ApiEndpoint::isActive).map(ApiEndpoint::getResourceId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            return new Catalog(resources.findTree(applicationId), dependencies.findByApplicationId(applicationId), inService);
        }));
        List<HealthFinding> findings = new ArrayList<>();
        Set<Long> granted = checkGrants(found, now, findings);
        checkDependencies(found, findings);
        checkActions(found, findings);
        checkApis(found, granted, findings);
        findings.sort(Comparator.comparing(HealthFinding::issue).thenComparing(HealthFinding::resourceCode)
                .thenComparing(finding -> String.valueOf(finding.relatedCode()))
                .thenComparing(finding -> String.valueOf(finding.tenantCode()))
                .thenComparing(finding -> String.valueOf(finding.roleCode())));
        return new HealthReport(applicationId, now, findings);
    }

    /** Reports grants that give nothing and returns the resources granted at all. */
    private Set<Long> checkGrants(Catalog found, Instant now, List<HealthFinding> findings)
    {
        Grants all = requireNonNull(TenantContext.callAsSystem(() -> everyTenant.execute(status -> {
            List<RoleGrant> given = InClauseBatcher.query(found.byId().keySet(), grants::findByResourceIdIn);
            Map<Long, Role> holders = InClauseBatcher.query(given.stream().map(RoleGrant::getRoleId).collect(Collectors.toSet()),
                    roles::findAllById).stream().collect(Collectors.toMap(Role::requireId, Function.identity()));
            Set<Long> tenantIds = holders.values().stream().map(Role::getTenantId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, String> codes = InClauseBatcher.query(tenantIds, tenants::findAllById).stream()
                    .collect(Collectors.toMap(Tenant::requireId, Tenant::getCode));
            return new Grants(given, holders, codes);
        })));
        for (RoleGrant grant : all.given()) {
            Resource resource = found.byId().get(grant.getResourceId());
            HealthIssue issue = grantIssue(found, resource, grant, now);
            if (resource != null && issue != null) {
                Role role = all.holders().get(grant.getRoleId());
                findings.add(new HealthFinding(issue, resource.requireId(), resource.getCode(), null, null,
                        role == null ? null : all.tenantCodes().get(role.getTenantId()), role == null ? null : role.getCode()));
            }
        }
        return all.given().stream().map(RoleGrant::getResourceId).collect(Collectors.toSet());
    }

    private static @Nullable HealthIssue grantIssue(Catalog found, @Nullable Resource resource, RoleGrant grant, Instant now)
    {
        if (resource == null) {
            return null;
        }
        if (!resource.isEnabled()) {
            return HealthIssue.GRANT_ON_DISABLED;
        }
        if (found.retired(resource)) {
            return HealthIssue.GRANT_ON_RETIRED_API;
        }
        return grant.appliesAt(now) ? null : HealthIssue.GRANT_EXPIRED;
    }

    private static void checkDependencies(Catalog found, List<HealthFinding> findings)
    {
        for (ResourceDependency dependency : found.dependencies()) {
            Resource dependent = found.byId().get(dependency.getResourceId());
            Resource target = found.byId().get(dependency.getDependsOnId());
            if (dependent == null || target == null) {
                continue;
            }
            HealthIssue issue = !target.isEnabled() ? HealthIssue.DEPENDENCY_ON_DISABLED
                    : found.retired(target) ? HealthIssue.DEPENDENCY_ON_RETIRED_API : null;
            if (issue != null) {
                findings.add(new HealthFinding(issue, dependent.requireId(), dependent.getCode(), target.requireId(),
                        target.getCode(), null, null));
            }
        }
    }

    /** Reports buttons from which no chain of dependencies reaches an API in service. */
    private static void checkActions(Catalog found, List<HealthFinding> findings)
    {
        Map<Long, List<Long>> needs = found.dependencies().stream().collect(Collectors.groupingBy(
                ResourceDependency::getResourceId, Collectors.mapping(ResourceDependency::getDependsOnId, Collectors.toList())));
        for (Resource action : found.byId().values()) {
            if (action.getType() == ResourceType.ACTION && action.isEnabled() && !reachesApi(found, needs, action.requireId())) {
                findings.add(new HealthFinding(HealthIssue.ACTION_WITHOUT_API, action.requireId(), action.getCode(), null, null,
                        null, null));
            }
        }
    }

    private static boolean reachesApi(Catalog found, Map<Long, List<Long>> needs, long start)
    {
        Set<Long> seen = new HashSet<>(Set.of(start));
        Deque<Long> queue = new ArrayDeque<>(seen);
        while (!queue.isEmpty()) {
            for (long next : needs.getOrDefault(queue.poll(), List.of())) {
                Resource resource = found.byId().get(next);
                if (resource != null && resource.getType() == ResourceType.API && resource.isEnabled() && !found.retired(resource)) {
                    return true;
                }
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /** Reports APIs in service that no resource needs and no role is granted. */
    private static void checkApis(Catalog found, Set<Long> granted, List<HealthFinding> findings)
    {
        Set<Long> needed = found.dependencies().stream().map(ResourceDependency::getDependsOnId).collect(Collectors.toSet());
        for (Resource api : found.byId().values()) {
            boolean live = api.getType() == ResourceType.API && api.isEnabled() && !found.retired(api);
            if (live && !needed.contains(api.requireId()) && !granted.contains(api.requireId())) {
                findings.add(new HealthFinding(HealthIssue.UNUSED_API, api.requireId(), api.getCode(), null, null, null, null));
            }
        }
    }

    /** An application's resources and dependencies, and the API resources some active endpoint offers. */
    private record Catalog(Map<Long, Resource> byId, List<ResourceDependency> dependencies, Set<Long> inService)
    {
        Catalog(List<Resource> tree, List<ResourceDependency> dependencies, Set<Long> inService)
        {
            this(tree.stream().collect(Collectors.toMap(Resource::requireId, Function.identity())), dependencies, inService);
        }

        boolean retired(Resource resource)
        {
            return resource.getType() == ResourceType.API && !inService.contains(resource.requireId());
        }
    }

    /** The grants of an application's resources in every tenant, their roles and the codes of their tenants. */
    private record Grants(List<RoleGrant> given, Map<Long, Role> holders, Map<Long, String> tenantCodes)
    {
    }
}
