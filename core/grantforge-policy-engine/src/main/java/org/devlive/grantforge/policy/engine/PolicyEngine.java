// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides access requests against a fixed set of policies; immutable and thread-safe. Build a new engine when the
 * policies change.
 *
 * <p>Override policies decide before normal ones. Among the policies of one priority that cover the resource and
 * apply at the time: a deny item that matches (and no deny exception of its policy) denies; otherwise an allow item
 * that matches (and no allow exception of its policy) allows; otherwise the normal policies are asked, and
 * without any decision access is denied as {@link Decision.Outcome#NOT_DETERMINED}. The deciding policy is the one
 * with the smallest id, so decisions do not depend on the order policies were given in.
 *
 * <p>An item matches when the user, one of the user's groups (or {@value PolicyItem#PUBLIC}) or roles is named,
 * one of its access types grants the requested one, and all of its conditions hold. A condition without an
 * evaluator, or whose evaluator fails, is taken in the safe direction: it holds for deny items and allow exceptions,
 * which take access away, and not for allow items and deny exceptions, which give it.
 *
 * <p>A request {@link AccessRequest#below() below} a resource counts allow items in the policies that cover the resource
 * or anything below it, and deny items only in those that cover the whole resource. A request for
 * {@link AccessRequest#ANY_ACCESS} is such a request, allowed when some access type is.
 */
public final class PolicyEngine
{
    private final ServiceModel model;
    private final List<CompiledPolicy> policies;
    private final PrefixIndex index;
    private final boolean indexed;
    private final Map<String, ConditionEvaluator> conditions;

    private PolicyEngine(ServiceModel model, List<CompiledPolicy> policies, Map<String, ConditionEvaluator> conditions,
            boolean indexed)
    {
        this.model = model;
        this.policies = policies;
        this.index = new PrefixIndex(policies);
        this.indexed = indexed;
        this.conditions = conditions;
    }

    /**
     * Builds an engine.
     *
     * @param model the service type
     * @param policies the policies
     * @param conditions the evaluators of condition types, by type
     * @return the engine
     * @throws IllegalArgumentException if a policy does not fit the model: an unknown or missing level, an unknown
     *         access type, a recursive value outside a path level or an invalid regular expression
     */
    public static PolicyEngine create(ServiceModel model, Collection<Policy> policies, Map<String, ConditionEvaluator> conditions)
    {
        return build(model, policies, conditions, true);
    }

    /** Builds an engine that tries every policy instead of using the prefix index; the tests compare the two. */
    static PolicyEngine withoutIndex(ServiceModel model, Collection<Policy> policies, Map<String, ConditionEvaluator> conditions)
    {
        return build(model, policies, conditions, false);
    }

    private static PolicyEngine build(ServiceModel model, Collection<Policy> policies, Map<String, ConditionEvaluator> conditions,
            boolean indexed)
    {
        Checks.notNull(model, "model");
        List<CompiledPolicy> compiled = new ArrayList<>();
        for (Policy policy : Checks.list(policies, "policies")) {
            compiled.add(new CompiledPolicy(policy, model));
        }
        return new PolicyEngine(model, Collections.unmodifiableList(compiled), Checks.map(conditions, "conditions"), indexed);
    }

    /**
     * Decides a request.
     *
     * @param request the request
     * @return the decision
     * @throws IllegalArgumentException if the request names an unknown level or its levels do not form a chain from a
     *         root
     */
    public Decision evaluate(AccessRequest request)
    {
        Map<String, String> normalized = normalize(request);
        BitSet candidates = candidates(normalized);
        if (AccessRequest.ANY_ACCESS.equals(request.accessType())) {
            return any(candidates, normalized, request);
        }
        Set<String> granting = model.grantedBy(request.accessType());
        if (granting.isEmpty()) {
            return Decision.notDetermined();
        }
        return decide(candidates, normalized, granting, request, request.below());
    }

    /** Decides each access type below the resource; the first allowed one, by the order of the model, decides. */
    private Decision any(BitSet candidates, Map<String, String> normalized, AccessRequest request)
    {
        Decision denied = Decision.notDetermined();
        for (String type : model.accessTypes()) {
            Decision decided = decide(candidates, normalized, model.grantedBy(type), request, true);
            if (decided.allowed()) {
                return decided;
            }
            if (decided.outcome() == Decision.Outcome.DENIED && denied.outcome() != Decision.Outcome.DENIED) {
                denied = decided;
            }
        }
        return denied;
    }

    private Decision decide(BitSet candidates, Map<String, String> normalized, Set<String> granting, AccessRequest request, boolean below)
    {
        Decision decided = decide(Priority.OVERRIDE, candidates, normalized, granting, request, below);
        if (decided.outcome() == Decision.Outcome.NOT_DETERMINED) {
            decided = decide(Priority.NORMAL, candidates, normalized, granting, request, below);
        }
        return decided;
    }

    /**
     * Finds the allow item that applies to a request, for policies whose items say what to do, such as masking and row
     * filtering, rather than whether access is allowed: unlike {@link #evaluate}, one policy decides. Override policies
     * come before normal ones; among the policies of one priority that cover the resource and apply at the time, the
     * one with the smallest id that has a matching allow item, and no matching allow exception, decides with its first
     * such item; for a request {@link AccessRequest#below() below} the resource, policies on what lies below it count too.
     * Deny items are not consulted. Items match as they do in {@link #evaluate}.
     *
     * @param request the request
     * @return the item, or {@code null} if no policy has one for the request
     * @throws IllegalArgumentException if the request names an unknown level or its levels do not form a chain from a
     *         root
     */
    public @Nullable ItemMatch select(AccessRequest request)
    {
        Map<String, String> normalized = normalize(request);
        Set<String> granting = model.grantedBy(request.accessType());
        if (granting.isEmpty()) {
            return null;
        }
        BitSet candidates = candidates(normalized);
        ItemMatch selected = select(Priority.OVERRIDE, candidates, normalized, granting, request);
        return selected != null ? selected : select(Priority.NORMAL, candidates, normalized, granting, request);
    }

    private BitSet candidates(Map<String, String> normalized)
    {
        if (indexed) {
            Map.Entry<String, String> root = normalized.entrySet().iterator().next();
            return index.candidates(root.getKey(), root.getValue());
        }
        BitSet all = new BitSet();
        all.set(0, policies.size());
        return all;
    }

    /** The requested values, root first and normalized, after checking that they form a chain from a root. */
    private Map<String, String> normalize(AccessRequest request)
    {
        String deepest = null;
        int depth = 0;
        for (String level : request.resource().keySet()) {
            if (model.level(level) == null) {
                throw new IllegalArgumentException("unknown resource level " + level);
            }
            int size = model.chain(level).size();
            if (size > depth) {
                depth = size;
                deepest = level;
            }
        }
        List<ResourceLevel> chain = model.chain(Checks.notNull(deepest, "resource"));
        if (chain.size() != request.resource().size()) {
            throw new IllegalArgumentException("requested levels " + request.resource().keySet() + " do not form a chain from a root");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (ResourceLevel level : chain) {
            String value = request.resource().get(level.name());
            if (value == null) {
                throw new IllegalArgumentException("requested levels " + request.resource().keySet() + " do not form a chain from a root");
            }
            normalized.put(level.name(), ValueMatcher.normalize(level, value));
        }
        return normalized;
    }

    private Decision decide(Priority priority, BitSet candidates, Map<String, String> normalized, Set<String> granting,
            AccessRequest request, boolean below)
    {
        @Nullable Long allowedBy = null;
        @Nullable Long deniedBy = null;
        for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1)) {
            CompiledPolicy compiled = policies.get(index);
            Policy policy = compiled.policy();
            if (policy.priority() != priority || !policy.appliesAt(request.time())) {
                continue;
            }
            boolean whole = compiled.covers(normalized);
            if (!whole && !(below && compiled.coversBelow(normalized))) {
                continue;
            }
            if (whole && any(policy.deny(), request, granting, true) && !any(policy.denyExceptions(), request, granting, false)) {
                deniedBy = smaller(deniedBy, policy.id());
            }
            else if (any(policy.allow(), request, granting, false) && !any(policy.allowExceptions(), request, granting, true)) {
                allowedBy = smaller(allowedBy, policy.id());
            }
        }
        if (deniedBy != null) {
            return Decision.deniedBy(deniedBy);
        }
        return allowedBy != null ? Decision.allowedBy(allowedBy) : Decision.notDetermined();
    }

    private static Long smaller(@Nullable Long current, long candidate)
    {
        return current == null || candidate < current ? Long.valueOf(candidate) : current;
    }

    private @Nullable ItemMatch select(Priority priority, BitSet candidates, Map<String, String> normalized, Set<String> granting,
            AccessRequest request)
    {
        long policyId = 0;
        int selected = -1;
        for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1)) {
            CompiledPolicy compiled = policies.get(index);
            Policy policy = compiled.policy();
            if (policy.priority() != priority || selected >= 0 && policy.id() >= policyId || !policy.appliesAt(request.time())
                    || !compiled.covers(normalized) && !(request.below() && compiled.coversBelow(normalized))) {
                continue;
            }
            int item = first(policy.allow(), request, granting, false);
            if (item >= 0 && !any(policy.allowExceptions(), request, granting, true)) {
                policyId = policy.id();
                selected = item;
            }
        }
        return selected < 0 ? null : new ItemMatch(policyId, selected);
    }

    /** Whether one of the items matches; {@code unsure} is what a condition counts as without a working evaluator. */
    private boolean any(List<PolicyItem> items, AccessRequest request, Set<String> granting, boolean unsure)
    {
        return first(items, request, granting, unsure) >= 0;
    }

    /** The position of the first item that matches, or -1. */
    private int first(List<PolicyItem> items, AccessRequest request, Set<String> granting, boolean unsure)
    {
        for (int index = 0; index < items.size(); index++) {
            PolicyItem item = items.get(index);
            if (names(item, request) && grants(item, granting) && conditionsHold(item, request, unsure)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean names(PolicyItem item, AccessRequest request)
    {
        if (item.users().contains(request.user()) || item.groups().contains(PolicyItem.PUBLIC)) {
            return true;
        }
        return !Collections.disjoint(item.groups(), request.groups()) || !Collections.disjoint(item.roles(), request.roles());
    }

    private static boolean grants(PolicyItem item, Set<String> granting)
    {
        return !Collections.disjoint(item.accessTypes(), granting);
    }

    private boolean conditionsHold(PolicyItem item, AccessRequest request, boolean unsure)
    {
        for (Condition condition : item.conditions()) {
            ConditionEvaluator evaluator = conditions.get(condition.type());
            boolean holds;
            if (evaluator == null) {
                holds = unsure;
            }
            else {
                try {
                    holds = evaluator.holds(condition.values(), request);
                }
                catch (RuntimeException failed) {
                    holds = unsure;
                }
            }
            if (!holds) {
                return false;
            }
        }
        return true;
    }
}
