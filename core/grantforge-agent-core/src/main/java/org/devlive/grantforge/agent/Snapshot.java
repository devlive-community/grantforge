// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.Condition;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;
import org.devlive.grantforge.policy.engine.Decision;
import org.devlive.grantforge.policy.engine.ItemMatch;
import org.devlive.grantforge.policy.engine.MatcherKind;
import org.devlive.grantforge.policy.engine.Policy;
import org.devlive.grantforge.policy.engine.PolicyEngine;
import org.devlive.grantforge.policy.engine.PolicyItem;
import org.devlive.grantforge.policy.engine.Priority;
import org.devlive.grantforge.policy.engine.ResourceLevel;
import org.devlive.grantforge.policy.engine.ResourceSpec;
import org.devlive.grantforge.policy.engine.ServiceModel;
import org.devlive.grantforge.policy.engine.Validity;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A policy snapshot of the agent's service, read from the JSON the server signs, with an engine for its access policies
 * and the roles and groups the snapshot gives each user. Immutable and thread-safe.
 *
 * <p>Masking and row filtering policies have engines of their own: one policy, and in it one allow item, says how a value
 * is masked or which rows are seen; see {@link #mask} and {@link #rowFilter}.
 */
public final class Snapshot
{
    /** The snapshot format this agent reads; the server raises it on incompatible changes. */
    public static final int FORMAT = 1;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String service;
    private final String serviceType;
    private final boolean serviceEnabled;
    private final long policyVersion;
    private final PolicyEngine engine;
    private final List<Policy> policies;
    private final int accessPolicies;
    private final int otherPolicies;
    private final Map<String, Set<String>> rolesOfUser;
    private final Map<String, Set<String>> groupsOfUser;
    private final Selecting masks;
    private final Selecting rowFilters;
    private final List<Policy> maskPolicies;
    private final List<Policy> rowFilterPolicies;
    private final Map<String, @Nullable String> transformers;

    private Snapshot(String service, String serviceType, boolean serviceEnabled, long policyVersion, PolicyEngine engine,
            List<Policy> policies, int otherPolicies, Map<String, Set<String>> rolesOfUser, Map<String, Set<String>> groupsOfUser,
            Selecting masks, Selecting rowFilters, List<Policy> maskPolicies, List<Policy> rowFilterPolicies,
            Map<String, @Nullable String> transformers)
    {
        this.service = service;
        this.serviceType = serviceType;
        this.serviceEnabled = serviceEnabled;
        this.policyVersion = policyVersion;
        this.engine = engine;
        this.policies = policies;
        this.accessPolicies = policies.size();
        this.otherPolicies = otherPolicies;
        this.rolesOfUser = rolesOfUser;
        this.groupsOfUser = groupsOfUser;
        this.masks = masks;
        this.rowFilters = rowFilters;
        this.maskPolicies = maskPolicies;
        this.rowFilterPolicies = rowFilterPolicies;
        this.transformers = transformers;
    }

    /** What an allow item of a masking policy (mask type and value) or a row filtering policy (condition) carries. */
    private static final class Carried
    {
        final String value;
        final @Nullable String extra;

        Carried(String value, @Nullable String extra)
        {
            this.value = value;
            this.extra = extra;
        }
    }

    /** The policies of one kind that select an item, with what each item carries. */
    private static final class Selecting
    {
        private final PolicyEngine engine;
        private final Map<Long, List<Carried>> items;

        Selecting(PolicyEngine engine, Map<Long, List<Carried>> items)
        {
            this.engine = engine;
            this.items = items;
        }

        @Nullable ItemMatch select(AccessRequest request)
        {
            return engine.select(request);
        }

        Carried item(ItemMatch match)
        {
            List<Carried> carried = items.get(match.policyId());
            if (carried == null) {
                throw new IllegalStateException("policy " + match.policyId() + " has no items");
            }
            return carried.get(match.item());
        }
    }

    /**
     * Reads a snapshot.
     *
     * @param body the snapshot as the server sends it
     * @param evaluators the condition evaluators the agent has, by evaluator name; conditions whose evaluator is missing
     *        are taken in the safe direction (see {@link PolicyEngine})
     * @return the snapshot
     * @throws IllegalArgumentException if it is not a snapshot of a format this agent reads, or its policies do not fit
     *         its service type
     */
    public static Snapshot parse(byte[] body, Map<String, ConditionEvaluator> evaluators)
    {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        }
        catch (IOException broken) {
            throw new IllegalArgumentException("the snapshot is not valid JSON", broken);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("the snapshot is not a JSON object");
        }
        long format = number(root, "format");
        if (format != FORMAT) {
            throw new IllegalArgumentException("snapshot format " + format + " is not supported; this agent reads format " + FORMAT
                    + ", upgrade it");
        }
        JsonNode definition = object(root, "definition");
        ServiceModel model = model(definition);
        Map<String, ConditionEvaluator> conditions = conditions(definition, evaluators);
        Map<String, @Nullable String> transformers = transformers(definition);
        List<Policy> policies = new ArrayList<>();
        List<Policy> maskPolicies = new ArrayList<>();
        List<Policy> filterPolicies = new ArrayList<>();
        Map<Long, List<Carried>> maskItems = new HashMap<>();
        Map<Long, List<Carried>> filterItems = new HashMap<>();
        int others = 0;
        for (JsonNode node : array(root, "policies")) {
            String type = text(node, "type");
            if ("ACCESS".equals(type)) {
                policies.add(policy(node));
            }
            else if ("DATA_MASK".equals(type)) {
                Policy policy = policy(node);
                maskPolicies.add(policy);
                maskItems.put(policy.id(), carried(node, policy.id(), true, transformers));
            }
            else if ("ROW_FILTER".equals(type)) {
                Policy policy = policy(node);
                filterPolicies.add(policy);
                filterItems.put(policy.id(), carried(node, policy.id(), false, transformers));
            }
            else {
                others++;
            }
        }
        return new Snapshot(text(root, "service"), text(root, "serviceType"), bool(root, "serviceEnabled"), number(root, "policyVersion"),
                PolicyEngine.create(model, policies, conditions), policies,
                others + maskPolicies.size() + filterPolicies.size(),
                byUser(root, "roles"), byUser(root, "groups"),
                new Selecting(PolicyEngine.create(model, maskPolicies, conditions), Collections.unmodifiableMap(maskItems)),
                new Selecting(PolicyEngine.create(model, filterPolicies, conditions), Collections.unmodifiableMap(filterItems)),
                maskPolicies, filterPolicies, transformers);
    }

    /** The mask types of the definition with their transformers; a type without one maps to {@code null}. */
    private static Map<String, @Nullable String> transformers(JsonNode definition)
    {
        Map<String, @Nullable String> transformers = new HashMap<>();
        for (JsonNode mask : optionalArray(definition, "maskTypes")) {
            transformers.put(text(mask, "name"), optionalText(mask, "transformer"));
        }
        return Collections.unmodifiableMap(transformers);
    }

    /** What each allow item of a masking (mask type and value) or row filtering (condition) policy carries. */
    private static List<Carried> carried(JsonNode node, long policyId, boolean masking, Map<String, @Nullable String> transformers)
    {
        List<Carried> carried = new ArrayList<>();
        for (JsonNode item : optionalArray(object(node, "document"), "allow")) {
            if (masking) {
                String maskType = text(item, "maskType");
                if (!transformers.containsKey(maskType)) {
                    throw new IllegalArgumentException("policy " + policyId + " masks with " + maskType + ", which the service type does not have");
                }
                carried.add(new Carried(maskType, optionalText(item, "maskValue")));
            }
            else {
                carried.add(new Carried(text(item, "rowFilter"), null));
            }
        }
        return Collections.unmodifiableList(carried);
    }

    private static ServiceModel model(JsonNode definition)
    {
        // The engine wants every level after its parent; the definition lists them in the service type's order.
        Map<String, JsonNode> pending = new LinkedHashMap<>();
        for (JsonNode level : array(definition, "resources")) {
            pending.put(text(level, "name"), level);
        }
        ServiceModel.Builder model = ServiceModel.builder();
        Set<String> added = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            boolean progress = false;
            Iterator<Map.Entry<String, JsonNode>> levels = pending.entrySet().iterator();
            while (levels.hasNext()) {
                JsonNode level = levels.next().getValue();
                String parent = optionalText(level, "parent");
                if (parent == null || added.contains(parent)) {
                    String name = text(level, "name");
                    model.level(ResourceLevel.of(name, parent, matcher(text(level, "matcher")), bool(level, "caseSensitive")));
                    added.add(name);
                    levels.remove();
                    progress = true;
                }
            }
            if (!progress) {
                throw new IllegalArgumentException("resource levels " + pending.keySet() + " have unknown parents");
            }
        }
        for (JsonNode access : array(definition, "accessTypes")) {
            List<String> implied = texts(access, "impliedGrants");
            model.accessType(text(access, "name"), implied.toArray(new String[0]));
        }
        return model.build();
    }

    private static MatcherKind matcher(String name)
    {
        try {
            return MatcherKind.valueOf(name);
        }
        catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("matcher " + name + " is not one this agent knows; upgrade it", unknown);
        }
    }

    private static Map<String, ConditionEvaluator> conditions(JsonNode definition, Map<String, ConditionEvaluator> evaluators)
    {
        Map<String, ConditionEvaluator> conditions = new HashMap<>();
        for (JsonNode condition : array(definition, "conditions")) {
            ConditionEvaluator evaluator = evaluators.get(text(condition, "evaluator"));
            if (evaluator != null) {
                conditions.put(text(condition, "name"), evaluator);
            }
        }
        return conditions;
    }

    private static Policy policy(JsonNode node)
    {
        String id = text(node, "id");
        long policyId;
        try {
            policyId = Long.parseLong(id);
        }
        catch (NumberFormatException broken) {
            throw new IllegalArgumentException("policy id " + id + " is not a number", broken);
        }
        JsonNode document = object(node, "document");
        Policy.Builder policy = Policy.builder(policyId).priority(Priority.valueOf(text(node, "priority")));
        Iterator<Map.Entry<String, JsonNode>> resources = object(document, "resources").fields();
        while (resources.hasNext()) {
            Map.Entry<String, JsonNode> resource = resources.next();
            JsonNode values = resource.getValue();
            policy.resource(resource.getKey(), ResourceSpec.of(texts(values, "values"), bool(values, "excludes"), bool(values, "recursive")));
        }
        policy.allow(items(document, "allow"));
        policy.allowExceptions(items(document, "allowExceptions"));
        policy.deny(items(document, "deny"));
        policy.denyExceptions(items(document, "denyExceptions"));
        for (JsonNode period : optionalArray(document, "validity")) {
            policy.validity(Validity.between(instant(period, "from"), instant(period, "until")));
        }
        return policy.build();
    }

    // One item per item of the document.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static PolicyItem[] items(JsonNode document, String name)
    {
        List<PolicyItem> items = new ArrayList<>();
        for (JsonNode item : optionalArray(document, name)) {
            List<Condition> conditions = new ArrayList<>();
            for (JsonNode condition : optionalArray(item, "conditions")) {
                conditions.add(Condition.of(text(condition, "type"), texts(condition, "values").toArray(new String[0])));
            }
            items.add(PolicyItem.builder()
                    .users(texts(item, "users").toArray(new String[0]))
                    .groups(texts(item, "groups").toArray(new String[0]))
                    .roles(texts(item, "roles").toArray(new String[0]))
                    .accessTypes(texts(item, "accessTypes").toArray(new String[0]))
                    .conditions(conditions.toArray(new Condition[0]))
                    .build());
        }
        return items.toArray(new PolicyItem[0]);
    }

    /** Turns "role: its users" around into "user: their roles". */
    // One set per user.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, Set<String>> byUser(JsonNode root, String name)
    {
        JsonNode holders = root.get(name);
        if (holders == null || holders.isNull()) {
            return Collections.emptyMap();
        }
        if (!holders.isObject()) {
            throw new IllegalArgumentException(name + " is not an object");
        }
        Map<String, Set<String>> byUser = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = holders.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if (!entry.getValue().isArray()) {
                throw new IllegalArgumentException("the members of " + entry.getKey() + " are not an array");
            }
            for (JsonNode user : entry.getValue()) {
                if (!user.isTextual()) {
                    throw new IllegalArgumentException("a member of " + entry.getKey() + " is not a name");
                }
                Set<String> of = byUser.get(user.asText());
                if (of == null) {
                    of = new LinkedHashSet<>();
                    byUser.put(user.asText(), of);
                }
                of.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Set<String>> entry : byUser.entrySet()) {
            entry.setValue(Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(byUser);
    }

    /**
     * Decides a request: adds the roles and groups the snapshot gives the user to those the system gave, then asks the
     * engine. A service that is not in use decides nothing, so the system's own checks apply.
     *
     * @param request the request
     * @return the decision
     */
    public AgentDecision decide(AccessRequest request)
    {
        if (!serviceEnabled) {
            return AgentDecision.notDetermined(policyVersion);
        }
        Decision decision = engine.evaluate(enrich(request));
        Long policy = decision.policyId();
        switch (decision.outcome()) {
            case ALLOWED:
                return AgentDecision.allowed(policyVersion, policy == null ? 0 : policy);
            case DENIED:
                return AgentDecision.denied(policyVersion, policy == null ? 0 : policy);
            default:
                return AgentDecision.notDetermined(policyVersion);
        }
    }

    /**
     * Decides whether a user may do something to some resource of a root level, whichever it is, such as writing to some
     * Kafka topic; see {@link PolicyEngine#evaluateAnyResource}. A service that is not in use decides nothing.
     *
     * @param request the user, the access type and the root level, under any value
     * @return the decision
     */
    public AgentDecision decideAnyResource(AccessRequest request)
    {
        if (!serviceEnabled) {
            return AgentDecision.notDetermined(policyVersion);
        }
        Decision decision = engine.evaluateAnyResource(enrich(request));
        Long policy = decision.policyId();
        switch (decision.outcome()) {
            case ALLOWED:
                return AgentDecision.allowed(policyVersion, policy == null ? 0 : policy);
            case DENIED:
                return AgentDecision.denied(policyVersion, policy == null ? 0 : policy);
            default:
                return AgentDecision.notDetermined(policyVersion);
        }
    }

    /**
     * Returns whether a policy could take an access away somewhere below a resource without taking it away from the
     * resource itself, after adding the roles and groups the snapshot gives the user; see
     * {@link PolicyEngine#restrictedBelow}. Agents that grant in the system's own terms ask it before granting a container
     * as a whole. A service that is not in use restricts nothing.
     *
     * @param request the user, the access type and the container
     * @return {@code true} if something below it may be decided otherwise
     */
    public boolean restrictedBelow(AccessRequest request)
    {
        return serviceEnabled && engine.restrictedBelow(enrich(request));
    }

    /**
     * Returns the access policies of the snapshot, as the engine reads them, for agents that show them in the system's
     * own terms, such as Kafka's ACLs.
     *
     * @return the policies, unmodifiable
     */
    public List<Policy> policies()
    {
        return Collections.unmodifiableList(policies);
    }

    /**
     * Returns the masking policies of the snapshot, as the engine reads them, for agents that must know where masks
     * could apply, such as before granting a pattern of resources as a whole.
     *
     * @return the policies, unmodifiable
     */
    public List<Policy> maskPolicies()
    {
        return Collections.unmodifiableList(maskPolicies);
    }

    /**
     * Returns the row filtering policies of the snapshot, as the engine reads them, for agents that must know where row
     * filters could apply.
     *
     * @return the policies, unmodifiable
     */
    public List<Policy> rowFilterPolicies()
    {
        return Collections.unmodifiableList(rowFilterPolicies);
    }

    /**
     * Returns the users the snapshot names: in the items of its policies of every kind, and among the members of its
     * roles and groups. Agents of systems whose users they cannot list all, such as those signing in through a
     * directory, keep these users too.
     *
     * @return the user names, sorted
     */
    public Set<String> users()
    {
        Set<String> users = new TreeSet<>(rolesOfUser.keySet());
        users.addAll(groupsOfUser.keySet());
        for (List<Policy> kind : Arrays.asList(policies, maskPolicies, rowFilterPolicies)) {
            for (Policy policy : kind) {
                for (List<PolicyItem> items : Arrays.asList(policy.allow(), policy.allowExceptions(), policy.deny(), policy.denyExceptions())) {
                    for (PolicyItem item : items) {
                        users.addAll(item.users());
                    }
                }
            }
        }
        return Collections.unmodifiableSet(users);
    }

    /**
     * Finds how a value is masked for a user: of the masking policies that cover the resource, override ones first and
     * then the one with the smallest id, the first allow item that matches the user and access type applies.
     *
     * @param request the user, the access (such as {@code select}) and the resource down to the masked level
     * @return the mask, or {@code null} when none applies or the service is not in use
     */
    public @Nullable DataMask mask(AccessRequest request)
    {
        if (!serviceEnabled) {
            return null;
        }
        ItemMatch match = masks.select(enrich(request));
        if (match == null) {
            return null;
        }
        Carried item = masks.item(match);
        return new DataMask(policyVersion, match.policyId(), item.value, transformers.get(item.value), item.extra);
    }

    /**
     * Finds the rows a user sees: of the row filtering policies that cover the resource, override ones first and then
     * the one with the smallest id, the first allow item that matches the user and access type applies.
     *
     * @param request the user, the access (such as {@code select}) and the resource down to the filtered level
     * @return the filter, or {@code null} when none applies or the service is not in use
     */
    public @Nullable RowFilter rowFilter(AccessRequest request)
    {
        if (!serviceEnabled) {
            return null;
        }
        ItemMatch match = rowFilters.select(enrich(request));
        return match == null ? null : new RowFilter(policyVersion, match.policyId(), rowFilters.item(match).value);
    }

    /**
     * Adds the roles and groups the snapshot gives the request's user.
     *
     * @param request the request
     * @return the request with them
     */
    AccessRequest enrich(AccessRequest request)
    {
        Set<String> groups = new LinkedHashSet<>(request.groups());
        groups.addAll(groupsOf(request.user()));
        Set<String> roles = new LinkedHashSet<>(request.roles());
        roles.addAll(rolesOf(request.user()));
        AccessRequest.Builder copy = AccessRequest.builder(request.user(), request.accessType())
                .groups(groups.toArray(new String[0]))
                .roles(roles.toArray(new String[0]))
                .time(request.time())
                .context(request.context())
                .below(request.below());
        for (Map.Entry<String, String> level : request.resource().entrySet()) {
            copy.resource(level.getKey(), level.getValue());
        }
        return copy.build();
    }

    /**
     * Returns the roles the snapshot gives a user.
     *
     * @param user the user name
     * @return the role codes
     */
    public Set<String> rolesOf(String user)
    {
        Set<String> roles = rolesOfUser.get(user);
        return roles == null ? Collections.<String>emptySet() : roles;
    }

    /**
     * Returns the groups the snapshot puts a user in.
     *
     * @param user the user name
     * @return the group codes
     */
    public Set<String> groupsOf(String user)
    {
        Set<String> groups = groupsOfUser.get(user);
        return groups == null ? Collections.<String>emptySet() : groups;
    }

    /**
     * Returns the service's name.
     *
     * @return the name
     */
    public String service()
    {
        return service;
    }

    /**
     * Returns the service type's name.
     *
     * @return the name
     */
    public String serviceType()
    {
        return serviceType;
    }

    /**
     * Returns whether the service is in use; a service that is not decides nothing.
     *
     * @return {@code true} if it is
     */
    public boolean serviceEnabled()
    {
        return serviceEnabled;
    }

    /**
     * Returns the policy version of the snapshot.
     *
     * @return the version
     */
    public long policyVersion()
    {
        return policyVersion;
    }

    /**
     * Returns how many access policies the engine holds.
     *
     * @return the count
     */
    public int accessPolicies()
    {
        return accessPolicies;
    }

    /**
     * Returns how many masking, row filtering and other policies the snapshot holds.
     *
     * @return the count
     */
    public int otherPolicies()
    {
        return otherPolicies;
    }

    private static JsonNode member(JsonNode node, String name)
    {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return value;
    }

    private static String text(JsonNode node, String name)
    {
        JsonNode value = member(node, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException(name + " is not a string");
        }
        return value.asText();
    }

    private static @Nullable String optionalText(JsonNode node, String name)
    {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : text(node, name);
    }

    private static long number(JsonNode node, String name)
    {
        JsonNode value = member(node, name);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(name + " is not a whole number");
        }
        return value.asLong();
    }

    private static boolean bool(JsonNode node, String name)
    {
        JsonNode value = member(node, name);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(name + " is not a boolean");
        }
        return value.asBoolean();
    }

    private static JsonNode object(JsonNode node, String name)
    {
        JsonNode value = member(node, name);
        if (!value.isObject()) {
            throw new IllegalArgumentException(name + " is not an object");
        }
        return value;
    }

    private static JsonNode array(JsonNode node, String name)
    {
        JsonNode value = member(node, name);
        if (!value.isArray()) {
            throw new IllegalArgumentException(name + " is not an array");
        }
        return value;
    }

    private static Iterable<JsonNode> optionalArray(JsonNode node, String name)
    {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? Collections.<JsonNode>emptyList() : array(node, name);
    }

    private static List<String> texts(JsonNode node, String name)
    {
        List<String> texts = new ArrayList<>();
        for (JsonNode value : optionalArray(node, name)) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException(name + " holds something other than strings");
            }
            texts.add(value.asText());
        }
        return texts;
    }

    private static @Nullable Instant instant(JsonNode node, String name)
    {
        String value = optionalText(node, name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        }
        catch (DateTimeParseException broken) {
            throw new IllegalArgumentException(name + " is not an instant", broken);
        }
    }
}
