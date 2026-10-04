// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DependencyGraph;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Works out what an account of the bound tenant may use: its active roles (direct and through groups, departments
 * and positions, within their validity, enabled), all their applying grants merged, and what they imply
 * ({@link GrantDerivation}: denials win across roles, ancestors become visible, required dependencies follow,
 * system roles allow their modules). Must be called with the account's tenant bound.
 */
@Service
public final class AuthorizationEvaluator
{
    private final EffectiveRoles effectiveRoles;
    /** Longest a snapshot is kept, even when nothing it depends on changes. */
    static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final AuthorizationVersions.Versions NO_VERSIONS = new AuthorizationVersions.Versions(-1, -1);

    private final RoleGrantRepository grants;
    private final AuthorizationVersions versions;
    private final Cache<CacheKey, Cached> cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(MAX_AGE).build();
    private final RoleRepository roles;
    private final RoleParentRepository parents;
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;
    private final ApplicationRepository applications;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the evaluator.
     *
     * @param effectiveRoles works out an account's roles
     * @param grants grants of the bound tenant
     * @param versions the counters cached snapshots depend on
     * @param roles roles of the bound tenant, for the roles others inherit from
     * @param parents inheritance between roles of the bound tenant
     * @param resources the resource catalog
     * @param dependencies dependencies, for what grants imply
     * @param applications applications, to find the console's own
     * @param transactionManager opens transactions
     * @param clock the current time, for validity and expiry
     */
    public AuthorizationEvaluator(EffectiveRoles effectiveRoles, RoleGrantRepository grants, AuthorizationVersions versions,
            RoleRepository roles,
            RoleParentRepository parents, ResourceRepository resources,
            ResourceDependencyRepository dependencies, ApplicationRepository applications,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.grants = requireNonNull(grants, "grants");
        this.versions = requireNonNull(versions, "versions");
        this.roles = requireNonNull(roles, "roles");
        this.parents = requireNonNull(parents, "parents");
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.applications = requireNonNull(applications, "applications");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Takes the snapshot of what an account may use in the console.
     *
     * @param accountId the account
     * @return the snapshot; empty for an account without roles
     */
    public AuthorizationSnapshot snapshot(long accountId)
    {
        return snapshotOf(accountId, null);
    }

    /**
     * Takes the snapshot of what an account may use in an application of the catalog, as the application asks through
     * the open API. System roles allow only the console's modules, so in other applications only grants count.
     *
     * @param accountId the account
     * @param applicationId the application
     * @return the snapshot; empty for an account without roles or grants in the application
     */
    public AuthorizationSnapshot snapshot(long accountId, long applicationId)
    {
        return snapshotOf(accountId, applicationId);
    }

    private AuthorizationSnapshot snapshotOf(long accountId, @Nullable Long requested)
    {
        return requireNonNull(transactions.execute(status -> {
            Instant now = clock.instant();
            long applicationId = requested != null ? requested
                    : applications.findByCode(Application.CONSOLE).map(Application::requireId).orElse(-1L);
            OptionalLong tenant = TenantContext.currentTenantId();
            if (tenant.isEmpty()) {
                return compute(accountId, applicationId, now).snapshot();
            }
            // The counters are read first: a change committed meanwhile raises them, so the next request recomputes.
            AuthorizationVersions.Versions current = versions.current(tenant.getAsLong());
            CacheKey key = new CacheKey(tenant.getAsLong(), accountId, applicationId);
            Cached cached = cache.getIfPresent(key);
            if (cached != null && cached.versions().equals(current) && now.isBefore(cached.validUntil())) {
                return cached.snapshot();
            }
            Cached computed = compute(accountId, applicationId, now);
            cache.put(key, new Cached(computed.snapshot(), current, computed.validUntil()));
            return computed.snapshot();
        }));
    }

    /** Works out a snapshot in an application, and until when time alone leaves it valid; within a transaction. */
    private Cached compute(long accountId, long applicationId, Instant now)
    {
        List<EffectiveRole> effective = effectiveRoles.of(accountId, now);
        List<RoleView> active = inherited(effective.stream().filter(EffectiveRole::active).map(EffectiveRole::role).toList());
        List<RoleGrant> applying = active.isEmpty() ? List.of() : grants.findByRoleIdIn(active.stream().map(RoleView::id).toList());
        Map<Long, Resource> usable = active.isEmpty() ? Map.of() : usable(active, catalog(applicationId), applying, now);
        // Assignments start and end, and grants expire, without any change: the snapshot holds until the next such moment.
        Instant validUntil = Stream.concat(effective.stream().flatMap(role -> role.sources().stream())
                        .flatMap(source -> Stream.of(source.terms().validFrom(), source.terms().validTo())),
                        applying.stream().map(RoleGrant::getExpiresAt))
                .filter(Objects::nonNull).filter(moment -> moment.isAfter(now)).min(Comparator.naturalOrder())
                .filter(moment -> moment.isBefore(now.plus(MAX_AGE))).orElse(now.plus(MAX_AGE));
        Set<String> ui = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        for (Resource resource : usable.values()) {
            if (resource.getType() == ResourceType.API) {
                String code = resource.getCode();
                permissions.add(code.startsWith(ApiCatalogService.RESOURCE_PREFIX)
                        ? code.substring(ApiCatalogService.RESOURCE_PREFIX.length()) : code);
            }
            else {
                ui.add(resource.getCode());
            }
        }
        return new Cached(new AuthorizationSnapshot(accountId, active.stream().map(RoleView::code).toList(), ui, permissions, now),
                NO_VERSIONS, validUntil);
    }

    /**
     * Returns the resources of an application an account may use, by ID; within a transaction.
     *
     * @param accountId the account
     * @param applicationId the application
     * @return the IDs of the usable resources
     */
    Set<Long> usableResources(long accountId, long applicationId)
    {
        Instant now = clock.instant();
        return usable(inherited(activeRoles(accountId, now)), applicationId, now).keySet();
    }

    /**
     * Returns the resources of an application that a set of roles would allow on their own, by ID; within a
     * transaction.
     *
     * @param roles the roles
     * @param applicationId the application
     * @return the IDs of the resources they make usable
     */
    Set<Long> coveredBy(Collection<RoleView> roles, long applicationId)
    {
        return usable(inherited(List.copyOf(roles)), applicationId, clock.instant()).keySet();
    }

    /**
     * Returns whether an actor has everything a role allows, with what it inherits, so the actor may hand it on (by
     * assigning it, or by letting another role inherit from it); within a transaction.
     *
     * @param actorId the actor
     * @param role the role
     * @return {@code true} if every resource the role makes usable is usable by the actor too
     */
    boolean covers(long actorId, RoleView role)
    {
        List<RoleView> expanded = inherited(List.of(role));
        Set<Long> applicationIds = new HashSet<>(resources.findAllById(grants.findByRoleIdIn(expanded.stream().map(RoleView::id)
                .toList()).stream().map(RoleGrant::getResourceId).collect(Collectors.toSet())).stream()
                .map(Resource::getApplicationId).toList());
        if (expanded.stream().anyMatch(view -> view.type() == RoleType.SYSTEM)) {
            applications.findByCode(Application.CONSOLE).ifPresent(console -> applicationIds.add(console.requireId()));
        }
        return applicationIds.stream().allMatch(applicationId -> usableResources(actorId, applicationId)
                .containsAll(coveredBy(List.of(role), applicationId)));
    }

    /**
     * Adds to roles every enabled role they inherit from, through enabled roles only: a disabled role passes nothing
     * on, not even what it inherits itself.
     */
    List<RoleView> inherited(List<RoleView> held)
    {
        if (held.isEmpty()) {
            return held;
        }
        RoleHierarchy hierarchy = new RoleHierarchy(parents.findAll());
        Map<Long, RoleView> found = new LinkedHashMap<>();
        held.forEach(role -> found.putIfAbsent(role.id(), role));
        Deque<Long> pending = new ArrayDeque<>(found.keySet());
        Map<Long, RoleView> all = null;
        while (!pending.isEmpty()) {
            List<Long> next = hierarchy.parentsOf(pending.removeFirst());
            if (next.isEmpty()) {
                continue;
            }
            if (all == null) {
                all = roles.findAll().stream().map(RoleView::from).collect(Collectors.toMap(RoleView::id, view -> view));
            }
            for (long parentId : next) {
                RoleView parent = all.get(parentId);
                if (parent != null && parent.enabled() && found.putIfAbsent(parentId, parent) == null) {
                    pending.addLast(parentId);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private List<RoleView> activeRoles(long accountId, Instant now)
    {
        return effectiveRoles.of(accountId, now).stream().filter(EffectiveRole::active).map(EffectiveRole::role).toList();
    }

    /** What roles, with what they inherit already added, make usable in an application. */
    private Map<Long, Resource> usable(List<RoleView> roles, long applicationId, Instant now)
    {
        if (roles.isEmpty()) {
            return Map.of();
        }
        CatalogView catalog = catalog(applicationId);
        return usable(roles, catalog, grants.findByRoleIdIn(roles.stream().map(RoleView::id).toList()), now);
    }

    /**
     * Loads an application's catalog for working out permissions; within a transaction.
     *
     * @param applicationId the application
     * @return its resources, dependencies and disabled resources
     */
    CatalogView catalog(long applicationId)
    {
        boolean console = applications.findById(applicationId).map(Application::getCode).filter(Application.CONSOLE::equals)
                .isPresent();
        return CatalogView.of(applicationId, console, resources.findTree(applicationId), dependencies.findByApplicationId(applicationId));
    }

    /**
     * Works out what roles, with what they inherit already added, make usable in a catalog with the given grants.
     *
     * @param roles the roles and the roles they inherit from
     * @param catalog the catalog
     * @param applying the roles' grants
     * @param now the current time, for expiry
     * @return the usable resources by ID; disabled resources and those below them are never usable, nor imply anything
     */
    Map<Long, Resource> usable(List<RoleView> roles, CatalogView catalog, List<RoleGrant> applying, Instant now)
    {
        Map<Long, Resource> byId = catalog.byId();
        Map<Long, GrantDerivation.ResourceState> states = states(roles, catalog, applying, now);
        Map<Long, Resource> found = new LinkedHashMap<>();
        states.forEach((id, state) -> {
            Resource resource = byId.get(id);
            if (resource != null && state.effective()) {
                found.put(id, resource);
            }
        });
        return found;
    }

    /**
     * Works out what roles' grants mean for every resource of a catalog, with the reasons; within a transaction.
     *
     * @param roles the roles, whose system roles allow their modules
     * @param catalog the catalog
     * @param applying the roles' grants
     * @param now the current time, for expiry
     * @return the states of the resources the grants touch; disabled resources and those below them take no part
     */
    static Map<Long, GrantDerivation.ResourceState> states(List<RoleView> roles, CatalogView catalog, List<RoleGrant> applying,
            Instant now)
    {
        // Disabled resources, and what lies below them, take no part: they grant nothing and bring nothing along.
        Map<Long, Resource> all = catalog.byId();
        List<Resource> inUse = catalog.tree().stream().filter(resource -> !catalog.switchedOff(all, resource.requireId())).toList();
        GrantDerivation derivation = new GrantDerivation(inUse, new DependencyGraph(catalog.dependencies()));
        return derivation.derive(applying, systemModules(roles, catalog), now);
    }

    /** The modules the system roles among the roles allow as a whole, if the catalog is the console's. */
    private static List<Long> systemModules(List<RoleView> roles, CatalogView catalog)
    {
        if (!catalog.console()) {
            return List.of();
        }
        Set<String> modules = roles.stream().filter(role -> role.type() == RoleType.SYSTEM)
                .flatMap(role -> SystemRole.byCode(role.code()).stream()).flatMap(role -> role.modules().stream())
                .collect(Collectors.toSet());
        return catalog.tree().stream().filter(resource -> resource.getParentId() == null && modules.contains(resource.getCode()))
                .map(Resource::requireId).toList();
    }

    /** Whose snapshot: an account of a tenant, in an application. */
    private record CacheKey(long tenantId, long accountId, long applicationId)
    {
    }

    /** A snapshot, the counters it was worked out at, and until when time alone leaves it valid. */
    private record Cached(AuthorizationSnapshot snapshot, AuthorizationVersions.Versions versions, Instant validUntil)
    {
    }
}
