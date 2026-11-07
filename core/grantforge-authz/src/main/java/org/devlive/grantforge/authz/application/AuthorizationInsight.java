// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceGrant;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.UserResourceGrant;
import org.devlive.grantforge.authz.domain.UserResourceGrantRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Answers whether accounts may use console resources and API permissions, and explains why: which roles allow them, how
 * the account holds those roles, and how the resource follows from what they are granted. Asking about an account
 * needs to be able to see it; the API checks the permission to ask. Every method must be called with the actor's tenant
 * bound.
 */
@Service
public final class AuthorizationInsight
{
    /** Most questions one check answers. */
    public static final int MAX_CHECKS = 100;

    /** Most changes one simulation tries. */
    public static final int MAX_CHANGES = 50;

    private final AuthorizationEvaluator evaluator;
    private final EffectiveRoles effectiveRoles;
    private final RoleGrantRepository grants;
    private final UserResourceGrantRepository directGrants;
    private final RoleRepository roles;
    private final RoleParentRepository parents;
    private final ApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final RowScopes scopes;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param evaluator works out what accounts may use
     * @param effectiveRoles the roles accounts hold and how
     * @param directGrants the accounts' direct grants
     * @param grants the roles' grants
     * @param roles the roles, for the roles others inherit from
     * @param parents which roles inherit from which
     * @param applications applications, to find the console's own
     * @param accounts accounts, to check the actor may see the one asked about
     * @param scopes the accounts each actor may see
     * @param transactionManager opens transactions
     * @param clock the current time, for validity and expiry
     */
    public AuthorizationInsight(AuthorizationEvaluator evaluator, EffectiveRoles effectiveRoles, RoleGrantRepository grants, UserResourceGrantRepository directGrants,
            RoleRepository roles, RoleParentRepository parents, ApplicationRepository applications, UserAccountRepository accounts,
            RowScopes scopes, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.grants = requireNonNull(grants, "grants");
        this.directGrants = requireNonNull(directGrants, "directGrants");
        this.roles = requireNonNull(roles, "roles");
        this.parents = requireNonNull(parents, "parents");
        this.applications = requireNonNull(applications, "applications");
        this.accounts = requireNonNull(accounts, "accounts");
        this.scopes = requireNonNull(scopes, "scopes");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Answers whether an account may use each of some resources and permissions now.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @param checks the questions, at most {@value #MAX_CHECKS}
     * @return the answers, in the order of the questions
     * @throws GrantForgeException with {@link CommonErrorCode#BAD_REQUEST} for too many questions, or
     *         {@link CommonErrorCode#NOT_FOUND} for an account the actor may not see
     */
    public List<AccessResult> check(long actorId, long accountId, List<AccessCheck> checks)
    {
        if (checks.size() > MAX_CHECKS) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "at most " + MAX_CHECKS + " checks at once");
        }
        requireVisible(actorId, accountId);
        AuthorizationSnapshot snapshot = evaluator.snapshot(accountId);
        return checks.stream().map(check -> new AccessResult(check.kind(), check.code(), check.kind() == AccessKind.PERMISSION
                ? snapshot.holds(check.code()) : snapshot.resources().contains(check.code()))).toList();
    }

    /**
     * Explains whether an account may use a resource or permission now.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @param check the question
     * @return the explanation
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an account the actor may not see
     */
    public AccessExplanation explain(long actorId, long accountId, AccessCheck check)
    {
        return explain(actorId, accountId, check, null);
    }

    /**
     * Explains a resource within the selected application.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @param check the question
     * @param applicationId the application, or null for the console
     * @return the explanation
     */
    public AccessExplanation explain(long actorId, long accountId, AccessCheck check, @Nullable Long applicationId)
    {
        requireVisible(actorId, accountId);
        return requireNonNull(transactions.execute(status -> explain(accountId, check, clock.instant(), application(applicationId))));
    }

    /**
     * Lists applications available for authorization inspection.
     *
     * @return the applications
     */
    public List<ApplicationView> applications()
    {
        return requireNonNull(transactions.execute(status -> applications.findOrdered().stream()
                .map(application -> ApplicationView.from(application, 0)).toList()));
    }

    /**
     * Lists everything an account may use in the console now, and the roles it has.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @return the access
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an account the actor may not see
     */
    public EffectiveAccess effective(long actorId, long accountId)
    {
        return effective(actorId, accountId, null);
    }

    /**
     * Lists effective access in the selected application.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @param applicationId the application, or null for the console
     * @return the access
     */
    public EffectiveAccess effective(long actorId, long accountId, @Nullable Long applicationId)
    {
        requireVisible(actorId, accountId);
        return requireNonNull(transactions.execute(status -> {
            long selected = application(applicationId);
            AuthorizationSnapshot snapshot = evaluator.snapshot(accountId, selected);
            CatalogView catalog = evaluator.catalog(selected);
            Map<Long, Resource> byId = catalog.byId();
            List<EffectiveAccess.Item> resources = new ArrayList<>();
            List<EffectiveAccess.Item> permissions = new ArrayList<>();
            for (Resource resource : catalog.tree()) {
                String code = resource.getCode();
                if (resource.getType() == ResourceType.API && snapshot.holds(permissionCode(code))) {
                    permissions.add(item(resource, permissionCode(code), byId));
                }
                else if (resource.getType() != ResourceType.API && snapshot.resources().contains(code)) {
                    resources.add(item(resource, code, byId));
                }
            }
            permissions.sort(Comparator.comparing(EffectiveAccess.Item::code));
            return new EffectiveAccess(effectiveRoles.of(accountId, clock.instant()), resources, permissions);
        }));
    }

    /**
     * Works out what an account would gain and lose if some changes were made, without making them.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @param simulation the changes
     * @return the differences
     * @throws GrantForgeException with {@link CommonErrorCode#BAD_REQUEST} for more than {@value #MAX_CHANGES} changes or a
     *         resource that cannot be granted, or {@link CommonErrorCode#NOT_FOUND} for an account the actor may not see, an
     *         unknown role or a resource the console's catalog does not have
     */
    public SimulationResult simulate(long actorId, long accountId, Simulation simulation)
    {
        return simulate(actorId, accountId, simulation, null);
    }

    /**
     * Simulates role changes in the selected application.
     *
     * @param actorId the account asking
     * @param accountId the account asked about
     * @param simulation the changes
     * @param applicationId the application, or null for the console
     * @return the differences
     * @throws GrantForgeException if too many changes are requested
     */
    public SimulationResult simulate(long actorId, long accountId, Simulation simulation, @Nullable Long applicationId)
    {
        if (simulation.addRoles().size() + simulation.removeRoles().size() + simulation.grants().size() > MAX_CHANGES) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "at most " + MAX_CHANGES + " changes at once");
        }
        requireVisible(actorId, accountId);
        return requireNonNull(transactions.execute(status -> simulate(accountId, simulation, clock.instant(), application(applicationId))));
    }

    private SimulationResult simulate(long accountId, Simulation simulation, Instant now, long applicationId)
    {
        CatalogView catalog = evaluator.catalog(applicationId);
        Map<Long, Resource> byId = catalog.byId();
        Map<Long, RoleView> known = roles.findAll().stream().map(RoleView::from).collect(Collectors.toMap(RoleView::id, Function.identity()));
        Set<Long> mentioned = new HashSet<>(simulation.addRoles());
        mentioned.addAll(simulation.removeRoles());
        simulation.grants().forEach(change -> mentioned.add(change.roleId()));
        mentioned.stream().filter(id -> !known.containsKey(id)).findFirst().ifPresent(id -> {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id);
        });
        simulation.grants().stream().map(change -> change.change().resourceId()).filter(id -> !byId.containsKey(id)).findFirst()
                .ifPresent(id -> {
                    throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no resource " + id);
                });

        List<RoleView> held = effectiveRoles.of(accountId, now).stream().filter(EffectiveRole::active).map(EffectiveRole::role).toList();
        Map<Long, RoleView> heldAfter = new LinkedHashMap<>();
        held.forEach(role -> heldAfter.put(role.id(), role));
        simulation.removeRoles().forEach(heldAfter::remove);
        simulation.addRoles().stream().map(known::get).filter(role -> requireNonNull(role).enabled())
                .forEach(role -> heldAfter.putIfAbsent(requireNonNull(role).id(), role));
        List<RoleView> before = evaluator.inherited(held);
        List<RoleView> after = evaluator.inherited(List.copyOf(heldAfter.values()));

        Set<Long> roleIds = new HashSet<>();
        before.forEach(role -> roleIds.add(role.id()));
        after.forEach(role -> roleIds.add(role.id()));
        List<RoleGrant> stored = roleIds.isEmpty() ? List.of() : grants.findByRoleIdIn(roleIds);
        List<RoleGrant> changed = changedGrants(stored, simulation.grants(), byId);
        List<UserResourceGrant> direct = directGrants.findByAccountId(accountId);
        Set<Long> usableBefore = usableIds(before, catalog, stored, direct, now);
        Set<Long> usableAfter = usableIds(after, catalog, changed, direct, now);
        List<EffectiveAccess.Item> gainedResources = new ArrayList<>();
        List<EffectiveAccess.Item> lostResources = new ArrayList<>();
        List<EffectiveAccess.Item> gainedPermissions = new ArrayList<>();
        List<EffectiveAccess.Item> lostPermissions = new ArrayList<>();
        for (Resource resource : catalog.tree()) {
            boolean was = usableBefore.contains(resource.requireId());
            boolean will = usableAfter.contains(resource.requireId());
            if (was == will) {
                continue;
            }
            boolean api = resource.getType() == ResourceType.API;
            EffectiveAccess.Item item = item(resource, api ? permissionCode(resource.getCode())
                    : resource.getCode(), byId);
            if (api) {
                (will ? gainedPermissions : lostPermissions).add(item);
            }
            else {
                (will ? gainedResources : lostResources).add(item);
            }
        }
        return new SimulationResult(before.stream().map(RoleView::code).toList(), after.stream().map(RoleView::code).toList(),
                gainedResources, lostResources, gainedPermissions, lostPermissions);
    }

    /** The stored grants with the simulated changes applied: a changed grant replaces the stored one of its role and resource. */
    private static List<RoleGrant> changedGrants(List<RoleGrant> stored, List<Simulation.RoleGrantChange> changes, Map<Long, Resource> byId)
    {
        Set<String> replaced = changes.stream().map(change -> change.roleId() + ":" + change.change().resourceId()).collect(Collectors.toSet());
        List<RoleGrant> result = new ArrayList<>(stored.stream()
                .filter(grant -> !replaced.contains(grant.getRoleId() + ":" + grant.getResourceId())).toList());
        changes.stream().filter(change -> change.change().effect() != null).map(change -> simulated(change, byId)).forEach(result::add);
        return result;
    }

    private static RoleGrant simulated(Simulation.RoleGrantChange change, Map<Long, Resource> byId)
    {
        Resource resource = requireNonNull(byId.get(change.change().resourceId()));
        try {
            return RoleGrant.create(change.roleId(), resource, requireNonNull(change.change().effect()), change.change().expiresAt(), 0);
        }
        catch (IllegalArgumentException ungrantable) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(ungrantable.getMessage()), ungrantable);
        }
    }

    private Set<Long> usableIds(List<RoleView> held, CatalogView catalog, List<RoleGrant> applying, List<UserResourceGrant> direct, Instant now)
    {
        Set<Long> ids = held.stream().map(RoleView::id).collect(Collectors.toSet());
        List<ResourceGrant> combined = new ArrayList<>(applying.stream().filter(grant -> ids.contains(grant.getRoleId())).toList());
        combined.addAll(direct);
        return evaluator.usableWithGrants(held, catalog, combined, now).keySet();
    }

    private static EffectiveAccess.Item item(Resource resource, String code, Map<Long, Resource> byId)
    {
        Long parentId = resource.getParentId();
        Resource parent = parentId == null ? null : byId.get(parentId);
        return new EffectiveAccess.Item(code, resource.getDetails().name(), resource.getNameKey(), resource.getType(),
                parent == null ? null : parent.getCode());
    }

    private AccessExplanation explain(long accountId, AccessCheck check, Instant now, long applicationId)
    {
        CatalogView catalog = evaluator.catalog(applicationId);
        Map<Long, Resource> byId = catalog.byId();
        boolean api = check.kind() == AccessKind.PERMISSION;
        String code = check.code();
        Resource target = byId.values().stream().filter(resource -> (api ? permissionCode(resource.getCode()) : resource.getCode()).equals(code)
                && (resource.getType() == ResourceType.API) == api).findFirst().orElse(null);
        if (target == null) {
            return new AccessExplanation(check.kind(), check.code(), AccessExplanation.Outcome.UNKNOWN, null, List.of(), List.of());
        }
        List<EffectiveRole> held = effectiveRoles.of(accountId, now).stream().filter(EffectiveRole::active).toList();
        Map<Long, List<RoleView>> chains = chains(held.stream().map(EffectiveRole::role).toList());
        Map<Long, List<Subject>> assignedTo = held.stream().collect(Collectors.toMap(role -> role.role().id(),
                role -> role.sources().stream().filter(AssignmentView::valid).map(AssignmentView::subject).toList()));
        List<RoleView> all = chains.values().stream().map(chain -> chain.get(chain.size() - 1)).toList();
        Map<Long, List<RoleGrant>> grantsOf = all.isEmpty() ? Map.of() : grants.findByRoleIdIn(all.stream().map(RoleView::id).toList())
                .stream().collect(Collectors.groupingBy(RoleGrant::getRoleId));
        List<UserResourceGrant> direct = directGrants.findByAccountId(accountId);
        List<ResourceGrant> combined = new ArrayList<>(grantsOf.values().stream().flatMap(List::stream).toList());
        combined.addAll(direct);
        boolean allowed = evaluator.usableWithGrants(all, catalog, combined, now).containsKey(target.requireId());
        List<AccessExplanation.Path> paths = new ArrayList<>();
        List<AccessExplanation.Denial> denials = new ArrayList<>();
        for (RoleView role : all) {
            Map<Long, GrantDerivation.ResourceState> states = AuthorizationEvaluator.states(List.of(role), catalog,
                    grantsOf.getOrDefault(role.id(), List.of()), now);
            GrantDerivation.ResourceState state = states.get(target.requireId());
            if (state == null) {
                continue;
            }
            if (state.state() == GrantDerivation.State.DENIED) {
                denials.add(denial(role, state, target, byId));
            }
            else {
                paths.add(new AccessExplanation.Path(roles(requireNonNull(chains.get(role.id())), assignedTo),
                        trail(target.requireId(), states, byId)));
            }
        }
        Map<Long, GrantDerivation.ResourceState> directStates = AuthorizationEvaluator.states(List.of(), catalog, direct, now);
        GrantDerivation.ResourceState directState = directStates.get(target.requireId());
        if (directState != null && directState.effective()) {
            List<AccessExplanation.PathResource> steps = new ArrayList<>(trail(target.requireId(), directStates, byId));
            if (!steps.isEmpty()) {
                AccessExplanation.PathResource first = steps.get(0);
                steps.set(0, new AccessExplanation.PathResource(first.code(), first.name(), first.type(), AccessExplanation.Via.DIRECT_GRANT));
                paths.add(new AccessExplanation.Path(List.of(), steps));
            }
        }
        AccessExplanation.Outcome outcome;
        if (allowed) {
            outcome = AccessExplanation.Outcome.ALLOWED;
        }
        else if (!denials.isEmpty()) {
            outcome = AccessExplanation.Outcome.DENIED;
        }
        else if (catalog.switchedOff(byId, target.requireId())) {
            outcome = AccessExplanation.Outcome.DISABLED;
        }
        else {
            outcome = AccessExplanation.Outcome.NOT_GRANTED;
        }
        return new AccessExplanation(check.kind(), check.code(), outcome, target.getDetails().name(), paths, denials);
    }

    /** For every role the held roles bring, the chain from the held role through the roles inherited on the way. */
    private Map<Long, List<RoleView>> chains(List<RoleView> held)
    {
        Map<Long, List<RoleView>> chains = new LinkedHashMap<>();
        held.forEach(role -> chains.putIfAbsent(role.id(), List.of(role)));
        if (held.isEmpty()) {
            return chains;
        }
        RoleHierarchy hierarchy = new RoleHierarchy(parents.findAll());
        Map<Long, RoleView> known = roles.findAll().stream().map(RoleView::from).collect(Collectors.toMap(RoleView::id, Function.identity()));
        Deque<Long> pending = new ArrayDeque<>(chains.keySet());
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            for (long parentId : hierarchy.parentsOf(current)) {
                RoleView parent = known.get(parentId);
                if (parent != null && parent.enabled() && !chains.containsKey(parentId)) {
                    chains.put(parentId, extended(requireNonNull(chains.get(current)), parent));
                    pending.addLast(parentId);
                }
            }
        }
        return chains;
    }

    private static List<RoleView> extended(List<RoleView> chain, RoleView next)
    {
        List<RoleView> longer = new ArrayList<>(chain);
        longer.add(next);
        return longer;
    }

    private static List<AccessExplanation.PathRole> roles(List<RoleView> chain, Map<Long, List<Subject>> assignedTo)
    {
        return chain.stream().map(role -> new AccessExplanation.PathRole(role.code(), role.name(),
                assignedTo.getOrDefault(role.id(), List.of()))).toList();
    }

    /** From the resource asked about back to what the role is granted, then turned around. */
    private static List<AccessExplanation.PathResource> trail(long targetId, Map<Long, GrantDerivation.ResourceState> states,
            Map<Long, Resource> byId)
    {
        List<AccessExplanation.PathResource> trail = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        long current = targetId;
        while (seen.add(current)) {
            GrantDerivation.ResourceState state = states.get(current);
            Resource resource = byId.get(current);
            if (state == null || resource == null) {
                break;
            }
            GrantDerivation.Reason reason = state.explicit() ? null : state.reasons().stream()
                    .filter(candidate -> candidate.via() != GrantDerivation.Via.DENIAL).findFirst().orElse(null);
            AccessExplanation.Via via = via(reason);
            trail.add(step(resource, via));
            // A grant or a system role is where the path starts; anything else follows from another resource.
            if (reason == null || via == AccessExplanation.Via.SYSTEM_ROLE) {
                break;
            }
            current = reason.resourceId();
        }
        Collections.reverse(trail);
        return trail;
    }

    private static AccessExplanation.Via via(GrantDerivation.@Nullable Reason reason)
    {
        if (reason == null) {
            return AccessExplanation.Via.GRANT;
        }
        return switch (reason.via()) {
            case ANCESTOR -> AccessExplanation.Via.ANCESTOR;
            case DEPENDENCY -> AccessExplanation.Via.DEPENDENCY;
            case SYSTEM_ROLE, DENIAL -> AccessExplanation.Via.SYSTEM_ROLE;
        };
    }

    private static AccessExplanation.PathResource step(Resource resource, AccessExplanation.Via via)
    {
        return new AccessExplanation.PathResource(resource.getCode(), resource.getDetails().name(), resource.getType(), via);
    }

    private static AccessExplanation.Denial denial(RoleView role, GrantDerivation.ResourceState state, Resource target,
            Map<Long, Resource> byId)
    {
        Resource denied = state.explicit() ? target : state.reasons().stream()
                .filter(reason -> reason.via() == GrantDerivation.Via.DENIAL).map(reason -> byId.get(reason.resourceId()))
                .filter(Objects::nonNull).findFirst().orElse(target);
        return new AccessExplanation.Denial(role.code(), role.name(), denied.getCode());
    }

    private long application(@Nullable Long requested)
    {
        if (requested != null) {
            return applications.findById(requested).orElseThrow(() ->
                    new GrantForgeException(CommonErrorCode.NOT_FOUND, "application not found")).requireId();
        }
        return applications.findByCode(Application.CONSOLE).map(Application::requireId).orElse(-1L);
    }

    private static String permissionCode(String code)
    {
        return code.startsWith(ApiCatalogService.RESOURCE_PREFIX) ? code.substring(ApiCatalogService.RESOURCE_PREFIX.length()) : code;
    }

    private void requireVisible(long actorId, long accountId)
    {
        transactions.executeWithoutResult(status -> scopes.requireWithin(actorId, accounts, UserAccount.class, DataAction.READ, accountId));
    }
}
