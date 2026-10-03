// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
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

    private final AuthorizationEvaluator evaluator;
    private final EffectiveRoles effectiveRoles;
    private final RoleGrantRepository grants;
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
     * @param grants the roles' grants
     * @param roles the roles, for the roles others inherit from
     * @param parents which roles inherit from which
     * @param applications applications, to find the console's own
     * @param accounts accounts, to check the actor may see the one asked about
     * @param scopes the accounts each actor may see
     * @param transactionManager opens transactions
     * @param clock the current time, for validity and expiry
     */
    public AuthorizationInsight(AuthorizationEvaluator evaluator, EffectiveRoles effectiveRoles, RoleGrantRepository grants,
            RoleRepository roles, RoleParentRepository parents, ApplicationRepository applications, UserAccountRepository accounts,
            RowScopes scopes, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.grants = requireNonNull(grants, "grants");
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
        requireVisible(actorId, accountId);
        return requireNonNull(transactions.execute(status -> explain(accountId, check, clock.instant())));
    }

    private AccessExplanation explain(long accountId, AccessCheck check, Instant now)
    {
        long console = applications.findByCode(Application.CONSOLE).map(Application::requireId).orElse(-1L);
        CatalogView catalog = evaluator.catalog(console);
        Map<Long, Resource> byId = catalog.byId();
        boolean api = check.kind() == AccessKind.PERMISSION;
        String code = api ? ApiCatalogService.RESOURCE_PREFIX + check.code() : check.code();
        Resource target = byId.values().stream().filter(resource -> resource.getCode().equals(code)
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
        boolean allowed = evaluator.usable(all, catalog, grantsOf.values().stream().flatMap(List::stream).toList(), now)
                .containsKey(target.requireId());
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

    private void requireVisible(long actorId, long accountId)
    {
        transactions.executeWithoutResult(status -> scopes.requireWithin(actorId, accounts, UserAccount.class, DataAction.READ, accountId));
    }
}
