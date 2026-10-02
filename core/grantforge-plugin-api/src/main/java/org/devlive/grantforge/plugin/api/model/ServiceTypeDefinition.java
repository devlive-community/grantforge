// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * What a kind of system (HDFS, Hive, the GrantForge console itself) looks like to the policy framework: the
 * resources it protects, what users can do with them, which policy types it supports and how a service of the
 * type is configured. Built definitions are consistent: every name a part refers to exists.
 *
 * @param name the type's name, unique among installed plugins, for example {@code hdfs}
 * @param label what the console shows
 * @param description a longer explanation, or {@code null}
 * @param version the definition's own version, raised by the plugin whenever the definition changes; at least 1
 * @param resources the resource levels, in the order the console lists them; at least one
 * @param accessTypes the access types, in the order the console lists them; at least one
 * @param dataMask data masking support, or {@code null}
 * @param rowFilter row filtering support, or {@code null}
 * @param conditions the conditions policy items may carry
 * @param configFields the settings of a service of this type, in form order
 * @param capabilities optional extensions the type opts into, such as {@code resource-dependencies}
 */
public record ServiceTypeDefinition(String name, String label, @Nullable String description, int version,
        List<ResourceDefinition> resources, List<AccessTypeDefinition> accessTypes, @Nullable DataMaskDefinition dataMask,
        @Nullable RowFilterDefinition rowFilter, List<ConditionDefinition> conditions, List<ConfigField> configFields,
        Set<String> capabilities)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if a value is malformed
     * @throws DefinitionException listing every problem if the parts do not fit together
     */
    public ServiceTypeDefinition
    {
        Names.name(name, "service type name");
        label = Names.label(label, "label of service type " + name);
        description = Names.optional(description);
        resources = List.copyOf(requireNonNull(resources, "resources"));
        accessTypes = List.copyOf(requireNonNull(accessTypes, "accessTypes"));
        conditions = List.copyOf(requireNonNull(conditions, "conditions"));
        configFields = List.copyOf(requireNonNull(configFields, "configFields"));
        capabilities = Set.copyOf(requireNonNull(capabilities, "capabilities"));
        for (String capability : capabilities) {
            Names.name(capability, "capability");
        }
        List<String> problems = new ArrayList<>();
        if (version < 1) {
            problems.add("version must be at least 1");
        }
        checkResources(resources, problems);
        checkAccessTypes(accessTypes, resources, problems);
        unique("condition", conditions.stream().map(ConditionDefinition::name).toList(), problems);
        unique("config field", configFields.stream().map(ConfigField::name).toList(), problems);
        Set<String> resourceNames = new HashSet<>(resources.stream().map(ResourceDefinition::name).toList());
        if (dataMask != null) {
            known("data mask resource", dataMask.resources(), resourceNames, problems);
        }
        if (rowFilter != null) {
            known("row filter resource", rowFilter.resources(), resourceNames, problems);
        }
        if (!problems.isEmpty()) {
            throw new DefinitionException(name, problems);
        }
    }

    private static void checkResources(List<ResourceDefinition> resources, List<String> problems)
    {
        if (resources.isEmpty()) {
            problems.add("at least one resource is required");
        }
        unique("resource", resources.stream().map(ResourceDefinition::name).toList(), problems);
        Map<String, @Nullable String> parents = new HashMap<>();
        resources.forEach(resource -> parents.put(resource.name(), resource.parent()));
        for (ResourceDefinition resource : resources) {
            String parent = resource.parent();
            if (parent != null && !parents.containsKey(parent)) {
                problems.add("resource " + resource.name() + " has an unknown parent " + parent);
                continue;
            }
            // Walking up more levels than there are means the chain loops.
            int steps = 0;
            while (parent != null && steps <= parents.size()) {
                parent = parents.get(parent);
                steps++;
            }
            if (parent != null) {
                problems.add("resource " + resource.name() + " is part of a parent cycle");
            }
        }
    }

    private static void checkAccessTypes(List<AccessTypeDefinition> accessTypes, List<ResourceDefinition> resources,
            List<String> problems)
    {
        if (accessTypes.isEmpty()) {
            problems.add("at least one access type is required");
        }
        List<String> names = accessTypes.stream().map(AccessTypeDefinition::name).toList();
        unique("access type", names, problems);
        Set<String> known = new HashSet<>(names);
        for (AccessTypeDefinition accessType : accessTypes) {
            known("access type implied by " + accessType.name(), accessType.impliedGrants(), known, problems);
        }
        for (ResourceDefinition resource : resources) {
            known("access type of resource " + resource.name(), resource.accessTypes(), known, problems);
        }
    }

    private static void unique(String what, List<String> names, List<String> problems)
    {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                problems.add(what + " " + name + " declared twice");
            }
        }
    }

    private static void known(String what, Set<String> names, Set<String> known, List<String> problems)
    {
        names.stream().filter(name -> !known.contains(name)).sorted()
                .forEach(name -> problems.add(what + " " + name + " is not declared"));
    }

    /**
     * Starts a definition with version 1 and the label equal to the name.
     *
     * @param name the type's name
     * @return a builder
     */
    public static Builder builder(String name)
    {
        return new Builder(name);
    }

    /**
     * Returns the policy types the type supports.
     *
     * @return {@link PolicyType#ACCESS} plus masking and row filtering when declared
     */
    public Set<PolicyType> policyTypes()
    {
        Set<PolicyType> types = EnumSet.of(PolicyType.ACCESS);
        if (dataMask != null) {
            types.add(PolicyType.DATA_MASK);
        }
        if (rowFilter != null) {
            types.add(PolicyType.ROW_FILTER);
        }
        return types;
    }

    /**
     * Finds a resource level.
     *
     * @param resourceName the level's name
     * @return the level, or empty
     */
    public Optional<ResourceDefinition> resource(String resourceName)
    {
        return resources.stream().filter(resource -> resource.name().equals(resourceName)).findFirst();
    }

    /**
     * Finds an access type.
     *
     * @param accessTypeName the access type's name
     * @return the access type, or empty
     */
    public Optional<AccessTypeDefinition> accessType(String accessTypeName)
    {
        return accessTypes.stream().filter(accessType -> accessType.name().equals(accessTypeName)).findFirst();
    }

    /**
     * Returns the levels directly below one, or the roots.
     *
     * @param parent a level's name, or {@code null} for the roots
     * @return the levels in declaration order
     */
    public List<ResourceDefinition> children(@Nullable String parent)
    {
        return resources.stream().filter(resource -> Objects.equals(resource.parent(), parent)).toList();
    }

    /**
     * Returns every chain of levels from a root down to a level without children, such as
     * {@code [database, table, column]} and {@code [database, udf]}.
     *
     * @return the chains of level names, roots in declaration order and children depth first
     */
    public List<List<String>> hierarchies()
    {
        List<List<String>> chains = new ArrayList<>();
        Deque<List<String>> pending = new ArrayDeque<>();
        List<ResourceDefinition> roots = children(null);
        for (int i = roots.size() - 1; i >= 0; i--) {
            pending.push(List.of(roots.get(i).name()));
        }
        while (!pending.isEmpty()) {
            List<String> chain = pending.pop();
            List<ResourceDefinition> below = children(chain.get(chain.size() - 1));
            if (below.isEmpty()) {
                chains.add(chain);
            }
            for (int i = below.size() - 1; i >= 0; i--) {
                pending.push(Stream.concat(chain.stream(), Stream.of(below.get(i).name())).toList());
            }
        }
        return chains;
    }

    /**
     * Returns whether a policy may name exactly these levels: a chain from a root, each level the child of the
     * one before, ending at a level without children or one marked {@link ResourceDefinition#validLeaf()}.
     *
     * @param resourceNames the levels, root first
     * @return whether the chain is a valid policy resource
     */
    public boolean isValidPolicyResource(List<String> resourceNames)
    {
        if (resourceNames.isEmpty()) {
            return false;
        }
        String parent = null;
        ResourceDefinition last = null;
        for (String resourceName : resourceNames) {
            ResourceDefinition level = resource(resourceName).orElse(null);
            if (level == null || !Objects.equals(level.parent(), parent)) {
                return false;
            }
            parent = level.name();
            last = level;
        }
        return requireNonNull(last).validLeaf() || children(last.name()).isEmpty();
    }

    /**
     * Returns the access types granting one grants, following implications transitively.
     *
     * @param accessTypeName an access type's name
     * @return the access type itself and every one it implies; empty if the name is not declared
     */
    public Set<String> impliedAccessTypes(String accessTypeName)
    {
        Set<String> granted = new LinkedHashSet<>();
        if (accessType(accessTypeName).isEmpty()) {
            return granted;
        }
        Deque<String> pending = new ArrayDeque<>();
        pending.push(accessTypeName);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (granted.add(current)) {
                accessType(current).ifPresent(type -> type.impliedGrants().stream().sorted().forEach(pending::push));
            }
        }
        return granted;
    }

    /**
     * Returns the access types that apply at a resource level.
     *
     * @param resourceName the level's name
     * @return the level's restriction, or every access type when it has none, in declaration order; empty for an
     *         unknown level
     */
    public List<String> accessTypesFor(String resourceName)
    {
        return resource(resourceName).map(level -> accessTypes.stream().map(AccessTypeDefinition::name)
                .filter(type -> level.accessTypes().isEmpty() || level.accessTypes().contains(type)).toList())
                .orElse(List.of());
    }

    /**
     * Checks a service's configuration against the fields. Blank values count as absent.
     *
     * @param values the configured values by field name
     * @return the problems, in field order and then for unknown fields; empty when the configuration is valid
     */
    public List<ConfigProblem> checkConfig(Map<String, String> values)
    {
        requireNonNull(values, "values");
        List<ConfigProblem> problems = new ArrayList<>();
        for (ConfigField field : configFields) {
            String value = values.get(field.name());
            if (value == null || value.isBlank()) {
                if (field.mandatory() && field.defaultValue() == null) {
                    problems.add(ConfigProblem.of(field.name(), ConfigProblem.Reason.REQUIRED));
                }
                continue;
            }
            field.check(value).ifPresent(reason -> problems.add(ConfigProblem.of(field.name(), reason)));
        }
        Set<String> declared = new HashSet<>(configFields.stream().map(ConfigField::name).toList());
        values.keySet().stream().filter(key -> !declared.contains(key)).sorted()
                .forEach(key -> problems.add(ConfigProblem.of(key, ConfigProblem.Reason.UNKNOWN_FIELD)));
        return problems;
    }

    /**
     * Fills in defaults for fields without a value.
     *
     * @param values the configured values by field name
     * @return the values in field order with defaults applied and blank values dropped
     */
    public Map<String, String> withDefaults(Map<String, String> values)
    {
        requireNonNull(values, "values");
        Map<String, String> resolved = new LinkedHashMap<>();
        for (ConfigField field : configFields) {
            String value = values.get(field.name());
            if (value == null || value.isBlank()) {
                value = field.defaultValue();
            }
            if (value != null) {
                resolved.put(field.name(), value);
            }
        }
        return resolved;
    }

    /** Builds a {@link ServiceTypeDefinition}. */
    public static final class Builder
    {
        private final String name;
        private String label;
        private @Nullable String description;
        private int version = 1;
        private final List<ResourceDefinition> resources = new ArrayList<>();
        private final List<AccessTypeDefinition> accessTypes = new ArrayList<>();
        private @Nullable DataMaskDefinition dataMask;
        private @Nullable RowFilterDefinition rowFilter;
        private final List<ConditionDefinition> conditions = new ArrayList<>();
        private final List<ConfigField> configFields = new ArrayList<>();
        private final Set<String> capabilities = new HashSet<>();

        private Builder(String name)
        {
            this.name = requireNonNull(name, "name");
            this.label = name;
        }

        /**
         * Sets what the console shows.
         *
         * @param value the label
         * @return this builder
         */
        public Builder label(String value)
        {
            label = value;
            return this;
        }

        /**
         * Sets the longer explanation.
         *
         * @param value the text
         * @return this builder
         */
        public Builder description(String value)
        {
            description = value;
            return this;
        }

        /**
         * Sets the definition's version.
         *
         * @param value at least 1
         * @return this builder
         */
        public Builder version(int value)
        {
            version = value;
            return this;
        }

        /**
         * Adds resource levels.
         *
         * @param values the levels
         * @return this builder
         */
        public Builder resources(ResourceDefinition... values)
        {
            resources.addAll(List.of(values));
            return this;
        }

        /**
         * Adds access types.
         *
         * @param values the access types
         * @return this builder
         */
        public Builder accessTypes(AccessTypeDefinition... values)
        {
            accessTypes.addAll(List.of(values));
            return this;
        }

        /**
         * Declares data masking support.
         *
         * @param value the support
         * @return this builder
         */
        public Builder dataMask(DataMaskDefinition value)
        {
            dataMask = value;
            return this;
        }

        /**
         * Declares row filtering support.
         *
         * @param value the support
         * @return this builder
         */
        public Builder rowFilter(RowFilterDefinition value)
        {
            rowFilter = value;
            return this;
        }

        /**
         * Adds conditions.
         *
         * @param values the conditions
         * @return this builder
         */
        public Builder conditions(ConditionDefinition... values)
        {
            conditions.addAll(List.of(values));
            return this;
        }

        /**
         * Adds configuration fields.
         *
         * @param values the fields
         * @return this builder
         */
        public Builder configFields(ConfigField... values)
        {
            configFields.addAll(List.of(values));
            return this;
        }

        /**
         * Opts into capabilities.
         *
         * @param values capability names
         * @return this builder
         */
        public Builder capabilities(String... values)
        {
            capabilities.addAll(List.of(values));
            return this;
        }

        /**
         * Builds the definition.
         *
         * @return the definition
         * @throws IllegalArgumentException if a value is malformed
         * @throws DefinitionException if the parts do not fit together
         */
        public ServiceTypeDefinition build()
        {
            return new ServiceTypeDefinition(name, label, description, version, resources, accessTypes, dataMask, rowFilter,
                    conditions, configFields, capabilities);
        }
    }
}
