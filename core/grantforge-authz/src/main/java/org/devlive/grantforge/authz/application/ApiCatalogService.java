// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.ApiEndpointRepository;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The API catalog: the endpoints the server serves, kept in step with the code at every start-up. Each permission
 * the code names becomes a built-in API resource {@code api:<permission>} of the console application, below the
 * built-in module {@value #API_MODULE}, so roles can grant it. Changes since the last review are marked.
 */
@Service
public final class ApiCatalogService
{
    /** Code of the built-in module that holds the API resources of new permissions. */
    public static final String API_MODULE = "api";

    /** Prefix of the API resource codes of permissions. */
    public static final String RESOURCE_PREFIX = "api:";

    private final ApiEndpointRepository endpoints;
    private final ResourceRepository resources;
    private final ApplicationService applications;
    private final CatalogAccess access;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param endpoints endpoints
     * @param resources resources, for the API resources of permissions
     * @param applications applications, for the console's own
     * @param access who may read and review the catalog
     * @param audit records reviews
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public ApiCatalogService(ApiEndpointRepository endpoints, ResourceRepository resources, ApplicationService applications,
            CatalogAccess access, AuditLog audit, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.endpoints = requireNonNull(endpoints, "endpoints");
        this.resources = requireNonNull(resources, "resources");
        this.applications = requireNonNull(applications, "applications");
        this.access = requireNonNull(access, "access");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Brings the catalog in step with what the code declares: records new endpoints, marks changed and vanished
     * ones, and creates the API resources of new permissions.
     *
     * @param declared every endpoint the server serves
     * @return what changed
     * @throws IllegalStateException if a route is declared twice or a permission code is malformed
     */
    public SyncReport synchronize(Collection<DeclaredEndpoint> declared)
    {
        check(declared);
        long console = applications.registerConsole();
        return requireNonNull(transactions.execute(status -> {
            Instant now = clock.instant();
            Map<String, Long> permissions = new HashMap<>();
            int created = 0;
            for (String permission : declared.stream().map(item -> item.declaration().permission())
                    .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new))) {
                Resource resource = resources.findByApplicationIdAndCode(console, RESOURCE_PREFIX + permission).orElse(null);
                if (resource == null) {
                    resource = resources.save(Resource.create(console, apiModule(console), ResourceType.API,
                            RESOURCE_PREFIX + permission, details(permission),
                            resources.findChildren(console, apiModule(console).requireId()).size()).markBuiltin());
                    created++;
                }
                else if (resource.getType() != ResourceType.API) {
                    throw new IllegalStateException("resource " + resource.getCode() + " exists but is no API resource");
                }
                else {
                    resource.markBuiltin();
                }
                permissions.put(permission, resource.requireId());
            }
            Map<String, ApiEndpoint> known = endpoints.findAll().stream()
                    .collect(Collectors.toMap(endpoint -> endpoint.getHttpMethod() + " " + endpoint.getPathPattern(),
                            Function.identity()));
            int added = 0;
            int changed = 0;
            for (DeclaredEndpoint endpoint : declared) {
                Long resourceId = resourceOf(endpoint, permissions);
                ApiEndpoint existing = known.remove(endpoint.route());
                if (existing == null) {
                    endpoints.save(ApiEndpoint.discover(endpoint.httpMethod(), endpoint.pathPattern(), endpoint.declaration(),
                            resourceId, now));
                    added++;
                }
                else if (existing.seen(endpoint.declaration(), resourceId, now)) {
                    changed++;
                }
            }
            int removed = (int) known.values().stream().filter(endpoint -> endpoint.vanish(now)).count();
            return new SyncReport(declared.size(), added, changed, removed, created);
        }));
    }

    /**
     * Lists every endpoint ever found, by path and method; removed ones stay for review and history.
     *
     * @param actorId the account asking
     * @return the endpoints
     */
    public List<ApiEndpointView> list(long actorId)
    {
        return requireNonNull(transactions.execute(status -> endpoints.findOrdered().stream()
                .map(ApiEndpointView::from).toList()));
    }

    /**
     * Confirms the changes of endpoints, clearing their marks.
     *
     * @param actorId the account asking
     * @param ids the endpoints; unknown ones and ones without a change are skipped
     * @return how many changes were confirmed
     */
    public int review(long actorId, Collection<Long> ids)
    {
        access.requireEditor(actorId);
        Set<Long> wanted = new HashSet<>(ids);
        int reviewed = requireNonNull(transactions.execute(status -> {
            List<ApiEndpoint> pending = endpoints.findAllById(wanted).stream().filter(endpoint -> endpoint.getChange() != null)
                    .toList();
            pending.forEach(ApiEndpoint::reviewed);
            return pending.size();
        }));
        if (reviewed > 0) {
            audit.record(new AuditRecord(AuditAction.API_CHANGES_REVIEWED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(),
                    actorId, null, null, Integer.toString(reviewed)));
        }
        return reviewed;
    }

    private Resource apiModule(long console)
    {
        return resources.findByApplicationIdAndCode(console, API_MODULE).orElseGet(() -> resources.save(Resource.create(
                console, null, ResourceType.MODULE, API_MODULE, new ResourceDetails("API", "Endpoints of the server, by permission",
                        null, true, true, DenyMode.HIDE), resources.findChildren(console, null).size()).markBuiltin()));
    }

    private static ResourceDetails details(String permission)
    {
        return new ResourceDetails(permission, null, null, true, true, DenyMode.HIDE);
    }

    private static @Nullable Long resourceOf(DeclaredEndpoint endpoint, Map<String, Long> permissions)
    {
        String permission = endpoint.declaration().permission();
        return permission == null ? null : permissions.get(permission);
    }

    private static void check(Collection<DeclaredEndpoint> declared)
    {
        Set<String> routes = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (DeclaredEndpoint endpoint : declared) {
            if (!routes.add(endpoint.route())) {
                problems.add(endpoint.route() + " is declared twice");
            }
            String permission = endpoint.declaration().permission();
            if (permission != null && !RequirePermission.CODE.matcher(permission).matches()) {
                problems.add(endpoint.declaration().handler() + ": malformed permission " + permission);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("invalid API declarations: " + String.join("; ", problems));
        }
    }
}
