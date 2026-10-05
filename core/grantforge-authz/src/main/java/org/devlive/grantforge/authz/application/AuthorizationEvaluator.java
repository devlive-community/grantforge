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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    // Loading an application's catalog and preparing its derivation grows with its resources (a hundred thousand
    // take most of a second), so snapshots share one per application until the catalog counter moves.
    private final Cache<Long, PreparedCatalog> catalogs = Caffeine.newBuilder().maximumSize(64).build();
    // Written once per catalog version and read by every request; a stale read only costs one lookup.
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile @Nullable ConsoleId console;
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
            OptionalLong tenant = TenantContext.currentTenantId();
            if (tenant.isEmpty()) {
                return compute(accountId, requested != null ? requested : consoleLookup(), now, null).snapshot();
            }
            // The counters are read first: a change committed meanwhile raises them, so the next request recomputes.
            AuthorizationVersions.Versions current = versions.current(tenant.getAsLong());
            long applicationId = requested != null ? requested : consoleId(current.catalog());
            CacheKey key = new CacheKey(tenant.getAsLong(), accountId, applicationId);
            Cached cached = cache.getIfPresent(key);
            if (cached != null && cached.versions().equals(current) && now.isBefore(cached.validUntil())) {
                return cached.snapshot();
            }
            Cached computed = compute(accountId, applicationId, now, current.catalog());
            cache.put(key, new Cached(computed.snapshot(), current, computed.validUntil()));
            return computed.snapshot();
        }));
    }

    /**
     * The console application's ID, looked up once per catalog version: every API call asks for the console snapshot,
     * and application changes raise the catalog version (B-076).
     */
    private long consoleId(long catalogVersion)
    {
        ConsoleId known = console;
        if (known != null && known.catalogVersion() == catalogVersion) {
            return known.id();
        }
        long id = consoleLookup();
        if (id > 0) {
            console = new ConsoleId(catalogVersion, id);
        }
        return id;
    }

    private long consoleLookup()
    {
        return applications.findByCode(Application.CONSOLE).map(Application::requireId).orElse(-1L);
    }

    /**
     * Works out a snapshot in an application, and until when time alone leaves it valid; within a transaction.
     *
     * @param catalogVersion the catalog counter read before, to share the prepared catalog; {@code null} to load it
     */
    private Cached compute(long accountId, long applicationId, Instant now, @Nullable Long catalogVersion)
    {
        List<EffectiveRole> effective = effectiveRoles.of(accountId, now);
        List<RoleView> active = inherited(effective.stream().filter(EffectiveRole::active).map(EffectiveRole::role).toList());
        List<RoleGrant> applying = active.isEmpty() ? List.of() : grants.findByRoleIdIn(active.stream().map(RoleView::id).toList());
        Map<Long, Resource> usable;
        if (active.isEmpty()) {
            usable = Map.of();
        }
        else if (catalogVersion == null) {
            usable = usable(active, catalog(applicationId), applying, now);
        }
        else {
            usable = prepared(applicationId, catalogVersion).usable(active, applying, now);
        }
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
     * Returns an application's catalog prepared for snapshots, loading it again once the catalog counter moved. The
     * counter was read before, so a catalog loaded now is at least as new as it: a change in between only makes the
     * next snapshot load it once more.
     */
    private PreparedCatalog prepared(long applicationId, long catalogVersion)
    {
        PreparedCatalog cached = catalogs.getIfPresent(applicationId);
        if (cached != null && cached.catalogVersion() == catalogVersion) {
            return cached;
        }
        CatalogView catalog = catalog(applicationId);
        PreparedCatalog fresh = new PreparedCatalog(catalogVersion, catalog, catalog.byId(), derivationOf(catalog));
        catalogs.put(applicationId, fresh);
        return fresh;
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
     * Returns the resources of an application an account may hand out to roles, by ID; within a transaction. In the
     * console that is what the account may use itself, so nobody gives more administration than they have. In other
     * applications the administrators of the tenant (holders of a system role) may hand out every resource: they set up
     * access to the tenant's applications, whose resources nobody holds before someone hands them out (D-69).
     *
     * @param accountId the account
     * @param applicationId the application
     * @return the IDs of the resources it may hand out
     */
    Set<Long> grantableResources(long accountId, long applicationId)
    {
        Instant now = clock.instant();
        List<RoleView> held = inherited(activeRoles(accountId, now));
        boolean console = applications.findById(applicationId).map(Application::getCode).filter(Application.CONSOLE::equals).isPresent();
        if (!console && held.stream().anyMatch(role -> role.type() == RoleType.SYSTEM)) {
            return resources.findTree(applicationId).stream().map(Resource::requireId).collect(Collectors.toSet());
        }
        return usable(held, applicationId, now).keySet();
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
     * Returns whether an actor may hand out everything a role allows, with what it inherits (by assigning it, or by
     * letting another role inherit from it): what the actor may grant ({@link #grantableResources}); within a transaction.
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
        return applicationIds.stream().allMatch(applicationId -> grantableResources(actorId, applicationId)
                .containsAll(coveredBy(List.of(role), applicationId)));
    }

    /**
     * Adds to roles every enabled role they inherit from, through enabled roles only: a disabled role passes nothing
     * on, not even what it inherits itself.
     */
    // Each level of inheritance is a list of its own.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    List<RoleView> inherited(List<RoleView> held)
    {
        if (held.isEmpty()) {
            return held;
        }
        RoleHierarchy hierarchy = new RoleHierarchy(parents.findAll());
        Map<Long, RoleView> found = new LinkedHashMap<>();
        held.forEach(role -> found.putIfAbsent(role.id(), role));
        List<Long> level = new ArrayList<>(found.keySet());
        // Level by level, loading only the parents met: a tenant may have thousands of roles, an account a few.
        while (!level.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (long roleId : level) {
                hierarchy.parentsOf(roleId).stream().filter(parentId -> !found.containsKey(parentId) && !next.contains(parentId))
                        .forEach(next::add);
            }
            Map<Long, RoleView> loaded = next.isEmpty() ? Map.of() : roles.findAllById(next).stream().map(RoleView::from)
                    .collect(Collectors.toMap(RoleView::id, view -> view));
            level = new ArrayList<>();
            for (long parentId : next) {
                RoleView parent = loaded.get(parentId);
                if (parent != null && parent.enabled() && found.putIfAbsent(parentId, parent) == null) {
                    level.add(parentId);
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
        return derivationOf(catalog).derive(applying, systemModules(roles, catalog), now);
    }

    /** Prepares the derivation of a catalog's resources that are in use. */
    private static GrantDerivation derivationOf(CatalogView catalog)
    {
        // Disabled resources, and what lies below them, take no part: they grant nothing and bring nothing along.
        Map<Long, Resource> all = catalog.byId();
        List<Resource> inUse = catalog.tree().stream().filter(resource -> !catalog.switchedOff(all, resource.requireId())).toList();
        return new GrantDerivation(inUse, new DependencyGraph(catalog.dependencies()));
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

    /**
     * An application's catalog loaded and prepared at a catalog counter; read-only, so snapshots of every thread share it.
     *
     * @param catalogVersion the catalog counter it was loaded at
     * @param catalog the catalog
     * @param byId its resources by ID
     * @param derivation the derivation of its resources in use
     */
    private record PreparedCatalog(long catalogVersion, CatalogView catalog, Map<Long, Resource> byId, GrantDerivation derivation)
    {
        /** What roles, with what they inherit already added, make usable with their grants; disabled resources never are. */
        Map<Long, Resource> usable(List<RoleView> roles, List<RoleGrant> applying, Instant now)
        {
            Map<Long, Resource> found = new LinkedHashMap<>();
            derivation.derive(applying, systemModules(roles, catalog), now).forEach((id, state) -> {
                Resource resource = byId.get(id);
                if (resource != null && state.effective()) {
                    found.put(id, resource);
                }
            });
            return found;
        }
    }

    /** The console application's ID as of a catalog version. */
    private record ConsoleId(long catalogVersion, long id)
    {
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
