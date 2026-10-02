// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
    private final RoleGrantRepository grants;
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
     * @param roles roles of the bound tenant, for the roles others inherit from
     * @param parents inheritance between roles of the bound tenant
     * @param resources the resource catalog
     * @param dependencies dependencies, for what grants imply
     * @param applications applications, to find the console's own
     * @param transactionManager opens transactions
     * @param clock the current time, for validity and expiry
     */
    public AuthorizationEvaluator(EffectiveRoles effectiveRoles, RoleGrantRepository grants, RoleRepository roles,
            RoleParentRepository parents, ResourceRepository resources,
            ResourceDependencyRepository dependencies, ApplicationRepository applications,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.grants = requireNonNull(grants, "grants");
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
        return requireNonNull(transactions.execute(status -> {
            Instant now = clock.instant();
            List<RoleView> active = inherited(activeRoles(accountId, now));
            long console = applications.findByCode(Application.CONSOLE).map(Application::requireId).orElse(-1L);
            Map<Long, Resource> usable = usable(active, console, now);
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
            return new AuthorizationSnapshot(accountId, active.stream().map(RoleView::code).toList(), ui, permissions, now);
        }));
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
    private List<RoleView> inherited(List<RoleView> held)
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
        List<Resource> tree = resources.findTree(applicationId);
        Map<Long, Resource> byId = tree.stream().collect(Collectors.toMap(Resource::requireId, resource -> resource));
        List<RoleGrant> applying = grants.findByRoleIdIn(roles.stream().map(RoleView::id).toList());
        GrantDerivation derivation = new GrantDerivation(tree, new DependencyGraph(dependencies.findByApplicationId(applicationId)));
        Map<Long, GrantDerivation.ResourceState> states = derivation.derive(applying, systemModules(roles, applicationId), now);
        return states.entrySet().stream().filter(entry -> entry.getValue().effective() && byId.containsKey(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> byId.get(entry.getKey())));
    }

    /** The modules the system roles among the roles allow as a whole, if the application is the console. */
    private List<Long> systemModules(List<RoleView> roles, long applicationId)
    {
        boolean console = applications.findById(applicationId).map(Application::getCode).filter(Application.CONSOLE::equals)
                .isPresent();
        if (!console) {
            return List.of();
        }
        return roles.stream().filter(role -> role.type() == RoleType.SYSTEM)
                .flatMap(role -> SystemRole.byCode(role.code()).stream()).flatMap(role -> role.modules().stream()).distinct()
                .flatMap(code -> resources.findByApplicationIdAndCode(applicationId, code).stream()).map(Resource::requireId)
                .toList();
    }
}
