// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConditionDefinition;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks a policy against its service type: the resource levels form one chain from the top down to a level a policy
 * may end at, every option is one the levels support, the items name someone and only access types, conditions and
 * masking methods the type knows, and only access policies have deny items and exceptions. Problems are reported per
 * input, such as {@code allow[0].accessTypes}.
 */
final class PolicyRules
{
    /** Most items in one list, values at one level, names in one item, labels and periods. */
    static final int MAX_ITEMS = 100;
    static final int MAX_VALUES = 100;
    static final int MAX_NAMES = 100;
    static final int MAX_LABELS = 10;
    static final int MAX_PERIODS = 10;

    private static final int NAME_LENGTH = 128;
    private static final int DESCRIPTION_LENGTH = 512;
    private static final int LABEL_LENGTH = 64;
    private static final int VALUE_LENGTH = 1024;
    private static final int EXPRESSION_LENGTH = 4000;

    private final ServiceTypeDefinition definition;
    private final PolicyType type;
    private final List<FieldIssue> issues = new ArrayList<>();

    private PolicyRules(ServiceTypeDefinition definition, PolicyType type)
    {
        this.definition = definition;
        this.type = type;
    }

    /**
     * Checks a policy.
     *
     * @param definition the service type
     * @param type what kind of policy it is
     * @param command the policy
     * @return the problems found; empty if the policy is sound
     */
    static List<FieldIssue> check(ServiceTypeDefinition definition, PolicyType type, PolicyCommand command)
    {
        PolicyRules rules = new PolicyRules(definition, type);
        if (!definition.policyTypes().contains(type)) {
            rules.issue("type", "error.policy.type-unsupported", type.name());
            return rules.issues;
        }
        rules.describe(command);
        Optional<ResourceDefinition> leaf = rules.resources(command.document().resources());
        PolicyDocument document = command.document();
        rules.items("allow", document.allow(), leaf);
        if (type == PolicyType.ACCESS) {
            rules.items("allowExceptions", document.allowExceptions(), leaf);
            rules.items("deny", document.deny(), leaf);
            rules.items("denyExceptions", document.denyExceptions(), leaf);
        }
        else {
            rules.absent("allowExceptions", document.allowExceptions());
            rules.absent("deny", document.deny());
            rules.absent("denyExceptions", document.denyExceptions());
        }
        rules.validity(document.validity());
        return rules.issues;
    }

    private void describe(PolicyCommand command)
    {
        if (command.name().isEmpty()) {
            issue("name", "error.policy.required");
        }
        else if (command.name().length() > NAME_LENGTH) {
            issue("name", "error.policy.too-long", NAME_LENGTH);
        }
        String description = command.description();
        if (description != null && description.length() > DESCRIPTION_LENGTH) {
            issue("description", "error.policy.too-long", DESCRIPTION_LENGTH);
        }
        if (command.labels().size() > MAX_LABELS) {
            issue("labels", "error.policy.too-many", MAX_LABELS);
        }
        else if (command.labels().stream().anyMatch(label -> label.length() > LABEL_LENGTH)) {
            issue("labels", "error.policy.too-long", LABEL_LENGTH);
        }
    }

    /** Checks the levels and their values; returns the lowest level when they form a proper chain. */
    private Optional<ResourceDefinition> resources(Map<String, ResourceValues> resources)
    {
        if (resources.isEmpty()) {
            issue("resources", "error.policy.required");
            return Optional.empty();
        }
        boolean known = true;
        for (Map.Entry<String, ResourceValues> entry : resources.entrySet()) {
            ResourceDefinition level = definition.resource(entry.getKey()).orElse(null);
            if (level == null) {
                issue("resources." + entry.getKey(), "error.policy.resource-unknown");
                known = false;
            }
            else {
                values(level, entry.getValue());
            }
        }
        if (!known) {
            return Optional.empty();
        }
        ResourceDefinition end = chain(resources.keySet()).orElse(null);
        if (end == null) {
            issue("resources", "error.policy.resources-not-a-chain");
            return Optional.empty();
        }
        if (!end.validLeaf() && !definition.children(end.name()).isEmpty()) {
            issue("resources", "error.policy.resources-incomplete", end.label());
        }
        @Nullable Set<String> supported = switch (type) {
            case ACCESS -> null;
            case DATA_MASK -> Optional.ofNullable(definition.dataMask()).map(DataMaskDefinition::resources).orElse(Set.of());
            case ROW_FILTER -> Optional.ofNullable(definition.rowFilter()).map(RowFilterDefinition::resources).orElse(Set.of());
        };
        if (supported != null && !supported.contains(end.name())) {
            issue("resources", "error.policy.resource-not-supported", end.label());
        }
        return Optional.of(end);
    }

    /** Returns the lowest level if the levels are one chain from a top level down, each directly below the one before. */
    private Optional<ResourceDefinition> chain(Set<String> levels)
    {
        List<ResourceDefinition> tops = definition.children(null).stream().filter(level -> levels.contains(level.name())).toList();
        if (tops.size() != 1) {
            return Optional.empty();
        }
        ResourceDefinition current = tops.get(0);
        int reached = 1;
        while (true) {
            List<ResourceDefinition> next = definition.children(current.name()).stream()
                    .filter(level -> levels.contains(level.name())).toList();
            if (next.size() > 1) {
                return Optional.empty();
            }
            if (next.isEmpty()) {
                return reached == levels.size() ? Optional.of(current) : Optional.empty();
            }
            current = next.get(0);
            reached++;
        }
    }

    private void values(ResourceDefinition level, ResourceValues values)
    {
        String field = "resources." + level.name();
        if (values.values().isEmpty()) {
            issue(field, "error.policy.required");
        }
        else if (values.values().size() > MAX_VALUES) {
            issue(field, "error.policy.too-many", MAX_VALUES);
        }
        else if (values.values().stream().anyMatch(value -> value.length() > VALUE_LENGTH)) {
            issue(field, "error.policy.too-long", VALUE_LENGTH);
        }
        if (values.excludes() && !level.excludesSupported()) {
            issue(field, "error.policy.excludes-unsupported");
        }
        if (values.recursive() && !level.recursiveSupported()) {
            issue(field, "error.policy.recursive-unsupported");
        }
    }

    private void items(String list, List<PolicyItemSpec> items, Optional<ResourceDefinition> leaf)
    {
        if (items.size() > MAX_ITEMS) {
            issue(list, "error.policy.too-many", MAX_ITEMS);
            return;
        }
        Set<String> accessTypes = leaf.map(ResourceDefinition::accessTypes).filter(names -> !names.isEmpty())
                .orElseGet(() -> definition.accessTypes().stream().map(AccessTypeDefinition::name).collect(Collectors.toSet()));
        for (int index = 0; index < items.size(); index++) {
            item(list + "[" + index + "]", items.get(index), accessTypes);
        }
    }

    private void item(String field, PolicyItemSpec item, Set<String> accessTypes)
    {
        if (item.users().isEmpty() && item.groups().isEmpty() && item.roles().isEmpty()) {
            issue(field + ".subjects", "error.policy.no-subject");
        }
        names(field + ".users", item.users());
        names(field + ".groups", item.groups());
        names(field + ".roles", item.roles());
        if (item.accessTypes().isEmpty()) {
            issue(field + ".accessTypes", "error.policy.required");
        }
        else {
            List<String> unknown = item.accessTypes().stream().filter(name -> !accessTypes.contains(name)).toList();
            if (!unknown.isEmpty()) {
                issue(field + ".accessTypes", "error.policy.access-type-unknown", String.join(", ", unknown));
            }
        }
        conditions(field, item.conditions());
        switch (type) {
            case ACCESS -> {
                notFor(field + ".maskType", item.maskType());
                notFor(field + ".rowFilter", item.rowFilter());
            }
            case DATA_MASK -> {
                mask(field, item);
                notFor(field + ".rowFilter", item.rowFilter());
            }
            case ROW_FILTER -> {
                String filter = item.rowFilter();
                if (filter == null) {
                    issue(field + ".rowFilter", "error.policy.required");
                }
                else if (filter.length() > EXPRESSION_LENGTH) {
                    issue(field + ".rowFilter", "error.policy.too-long", EXPRESSION_LENGTH);
                }
                notFor(field + ".maskType", item.maskType());
            }
        }
    }

    private void names(String field, List<String> names)
    {
        if (names.size() > MAX_NAMES) {
            issue(field, "error.policy.too-many", MAX_NAMES);
        }
    }

    private void conditions(String field, List<ConditionValues> conditions)
    {
        for (ConditionValues condition : conditions) {
            String path = field + ".conditions." + condition.type();
            if (definition.conditions().stream().map(ConditionDefinition::name).noneMatch(condition.type()::equals)) {
                issue(path, "error.policy.condition-unknown");
            }
            else if (condition.values().isEmpty()) {
                issue(path, "error.policy.required");
            }
            else if (condition.values().size() > MAX_VALUES) {
                issue(path, "error.policy.too-many", MAX_VALUES);
            }
        }
    }

    private void mask(String field, PolicyItemSpec item)
    {
        String maskType = item.maskType();
        List<MaskTypeDefinition> known = Optional.ofNullable(definition.dataMask()).map(DataMaskDefinition::maskTypes).orElse(List.of());
        if (maskType == null) {
            issue(field + ".maskType", "error.policy.required");
        }
        else if (known.stream().map(MaskTypeDefinition::name).noneMatch(maskType::equals)) {
            issue(field + ".maskType", "error.policy.mask-type-unknown");
        }
        String value = item.maskValue();
        if (value != null && value.length() > EXPRESSION_LENGTH) {
            issue(field + ".maskValue", "error.policy.too-long", EXPRESSION_LENGTH);
        }
    }

    private void notFor(String field, @Nullable String value)
    {
        if (value != null) {
            issue(field, "error.policy.not-for-type");
        }
    }

    private void absent(String list, List<PolicyItemSpec> items)
    {
        if (!items.isEmpty()) {
            issue(list, "error.policy.not-for-type");
        }
    }

    private void validity(List<ValidityPeriod> periods)
    {
        if (periods.size() > MAX_PERIODS) {
            issue("validity", "error.policy.too-many", MAX_PERIODS);
            return;
        }
        for (int index = 0; index < periods.size(); index++) {
            ValidityPeriod period = periods.get(index);
            Instant from = period.from();
            Instant until = period.until();
            if (from == null && until == null) {
                issue("validity[" + index + "]", "error.policy.validity-empty");
            }
            else if (from != null && until != null && !until.isAfter(from)) {
                issue("validity[" + index + "]", "error.policy.validity-order");
            }
        }
    }

    private void issue(String field, String messageKey, Object... arguments)
    {
        issues.add(FieldIssue.of(field, messageKey, arguments));
    }
}
