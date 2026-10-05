// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.DataPolicy;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads conditions from JSON, checking them against a secured entity, and writes them back. The grammar:
 * <pre>{@code
 * {"and": [condition, ...]}   {"or": [condition, ...]}   {"not": condition}
 * {"field": "status", "op": "eq", "value": "ACTIVE"}
 * {"field": "unitId", "op": "in", "value": {"var": "subject.orgUnitIds"}}
 * {"field": "email", "op": "is_null"}
 * }</pre>
 * Fields must be filterable fields of the entity, operators must suit the field, values must be of the field's kind
 * (choices among the field's choices, moments as ISO-8601 instants) and variables of the same kind. Conditions nest
 * at most {@value #MAX_DEPTH} groups deep and hold at most {@value #MAX_NODES} parts; lists hold at most
 * {@value #MAX_VALUES} values and texts at most {@value #MAX_TEXT} characters; written out, a condition takes at most
 * {@value #MAX_LENGTH} characters.
 */
public final class ConditionCodec
{
    /** Deepest nesting of and, or and not. */
    public static final int MAX_DEPTH = 5;

    /** Most parts of a condition. */
    public static final int MAX_NODES = 50;

    /** Most values of a list. */
    public static final int MAX_VALUES = 100;

    /** Longest text value. */
    public static final int MAX_TEXT = 256;

    /** Longest condition as stored (compact JSON). */
    public static final int MAX_LENGTH = DataPolicy.CONDITION_MAX;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PREFIX = "error.data.condition.";

    private final SecuredEntityDefinition entity;
    private final List<FieldIssue> issues = new ArrayList<>();
    private int nodes;

    private ConditionCodec(SecuredEntityDefinition entity)
    {
        this.entity = entity;
    }

    /**
     * Reads a condition.
     *
     * @param json the condition as JSON
     * @param entity the entity whose fields it tests
     * @param path the input the condition came in, such as {@code condition}, for problems
     * @return the condition, or the problems found
     */
    public static Result read(String json, SecuredEntityDefinition entity, String path)
    {
        JsonNode tree;
        try {
            tree = JSON.readTree(json);
        }
        catch (JacksonException malformed) {
            return new Result(null, List.of(FieldIssue.of(path, PREFIX + "malformed")));
        }
        return read(tree, entity, path);
    }

    /**
     * Reads a condition.
     *
     * @param tree the condition
     * @param entity the entity whose fields it tests
     * @param path the input the condition came in, for problems
     * @return the condition, or the problems found
     */
    public static Result read(JsonNode tree, SecuredEntityDefinition entity, String path)
    {
        ConditionCodec codec = new ConditionCodec(entity);
        Condition condition = codec.node(tree, path, 0);
        if (codec.nodes > MAX_NODES) {
            codec.issue(path, "too-large", MAX_NODES);
        }
        if (codec.issues.isEmpty() && condition != null && write(condition).length() > MAX_LENGTH) {
            codec.issue(path, "too-long", MAX_LENGTH);
        }
        return codec.issues.isEmpty() ? new Result(condition, List.of()) : new Result(null, codec.issues);
    }

    /**
     * Writes a condition as compact JSON, in the grammar it was read in.
     *
     * @param condition the condition
     * @return the JSON
     */
    public static String write(Condition condition)
    {
        return JSON.writeValueAsString(tree(condition));
    }

    private static JsonNode tree(Condition condition)
    {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        if (condition instanceof Condition.AllOf all) {
            return group(nodes, "and", all.conditions());
        }
        if (condition instanceof Condition.AnyOf any) {
            return group(nodes, "or", any.conditions());
        }
        if (condition instanceof Condition.Negation negation) {
            return nodes.objectNode().set("not", tree(negation.condition()));
        }
        Condition.Comparison comparison = (Condition.Comparison) condition;
        ObjectNode node = nodes.objectNode().put("field", comparison.field()).put("op", comparison.operator().symbol());
        ConditionVariable variable = comparison.variable();
        Object value = comparison.value();
        if (variable != null) {
            node.set("value", nodes.objectNode().put("var", variable.key()));
        }
        else if (value != null) {
            node.set("value", literal(nodes, value));
        }
        return node;
    }

    private static ObjectNode group(JsonNodeFactory nodes, String name, List<Condition> conditions)
    {
        ArrayNode members = nodes.arrayNode();
        conditions.forEach(condition -> members.add(tree(condition)));
        return nodes.objectNode().set(name, members);
    }

    private static JsonNode literal(JsonNodeFactory nodes, Object value)
    {
        if (value instanceof List<?> values) {
            ArrayNode array = nodes.arrayNode();
            values.forEach(element -> array.add(literal(nodes, element)));
            return array;
        }
        if (value instanceof BigDecimal number) {
            return nodes.numberNode(number);
        }
        if (value instanceof Boolean flag) {
            return nodes.booleanNode(flag);
        }
        return nodes.stringNode(value.toString());
    }

    private @Nullable Condition node(@Nullable JsonNode node, String path, int depth)
    {
        nodes++;
        if (node == null || !node.isObject()) {
            issue(path, "malformed");
            return null;
        }
        if (node.has("and") || node.has("or")) {
            String name = node.has("and") ? "and" : "or";
            if (node.size() != 1) {
                issue(path, "malformed");
                return null;
            }
            Optional<List<Condition>> members = members(node.get(name), path + "." + name, depth + 1);
            if (members.isEmpty()) {
                return null;
            }
            return "and".equals(name) ? new Condition.AllOf(members.orElseThrow()) : new Condition.AnyOf(members.orElseThrow());
        }
        if (node.has("not")) {
            if (node.size() != 1) {
                issue(path, "malformed");
                return null;
            }
            if (depth + 1 > MAX_DEPTH) {
                issue(path, "too-deep", MAX_DEPTH);
                return null;
            }
            Condition inner = node(node.get("not"), path + ".not", depth + 1);
            return inner == null ? null : new Condition.Negation(inner);
        }
        return comparison(node, path);
    }

    /** Reads the members of a group; empty if the group has problems. */
    private Optional<List<Condition>> members(@Nullable JsonNode array, String path, int depth)
    {
        if (array == null || !array.isArray()) {
            issue(path, "malformed");
            return Optional.empty();
        }
        if (array.isEmpty()) {
            issue(path, "empty-group");
            return Optional.empty();
        }
        if (depth > MAX_DEPTH) {
            issue(path, "too-deep", MAX_DEPTH);
            return Optional.empty();
        }
        List<Condition> members = new ArrayList<>();
        int index = 0;
        boolean sound = true;
        for (JsonNode member : array.values()) {
            Condition condition = node(member, path + "[" + index + "]", depth);
            index++;
            if (condition == null) {
                sound = false;
            }
            else {
                members.add(condition);
            }
        }
        return sound ? Optional.of(members) : Optional.empty();
    }

    private @Nullable Condition comparison(JsonNode node, String path)
    {
        for (String name : node.propertyNames()) {
            if (!List.of("field", "op", "value").contains(name)) {
                issue(path, "malformed");
                return null;
            }
        }
        JsonNode fieldNode = node.get("field");
        JsonNode opNode = node.get("op");
        if (fieldNode == null || !fieldNode.isString() || opNode == null || !opNode.isString()) {
            issue(path, "malformed");
            return null;
        }
        Optional<DataField> field = entity.field(fieldNode.stringValue());
        if (field.isEmpty()) {
            issue(path + ".field", "field-unknown", fieldNode.stringValue());
            return null;
        }
        Optional<ComparisonOperator> operator = ComparisonOperator.of(opNode.stringValue());
        if (operator.isEmpty()) {
            issue(path + ".op", "operator-unknown", opNode.stringValue());
            return null;
        }
        DataField target = field.orElseThrow();
        ComparisonOperator op = operator.orElseThrow();
        if (!op.appliesTo(target.type())) {
            issue(path + ".op", "operator-not-for-field", op.symbol(), target.name());
            return null;
        }
        JsonNode value = node.get("value");
        if (!op.takesValue()) {
            if (value != null) {
                issue(path + ".value", "value-unexpected");
                return null;
            }
            return new Condition.Comparison(target.code(), op, null, null);
        }
        if (value == null || value.isNull()) {
            issue(path + ".value", "value-required");
            return null;
        }
        if (value.isObject()) {
            return variable(value, target, op, path + ".value");
        }
        Object literal = op.takesList() ? list(value, target, path + ".value").orElse(null) : scalar(value, target, path + ".value");
        return literal == null ? null : new Condition.Comparison(target.code(), op, literal, null);
    }

    private @Nullable Condition variable(JsonNode value, DataField field, ComparisonOperator op, String path)
    {
        JsonNode name = value.get("var");
        if (name == null || !name.isString() || value.size() != 1) {
            issue(path, "malformed");
            return null;
        }
        Optional<ConditionVariable> variable = ConditionVariable.of(name.stringValue());
        if (variable.isEmpty()) {
            issue(path, "variable-unknown", name.stringValue());
            return null;
        }
        ConditionVariable found = variable.orElseThrow();
        if (found.type() != field.type() || found.list() != op.takesList()) {
            issue(path, "variable-mismatch", found.key());
            return null;
        }
        return new Condition.Comparison(field.code(), op, null, found);
    }

    /** Reads a list of values; empty if it has problems. */
    private Optional<List<Object>> list(JsonNode value, DataField field, String path)
    {
        if (!value.isArray() || value.isEmpty()) {
            issue(path, "value-invalid", field.type().name());
            return Optional.empty();
        }
        if (value.size() > MAX_VALUES) {
            issue(path, "too-many-values", MAX_VALUES);
            return Optional.empty();
        }
        List<Object> values = new ArrayList<>();
        int index = 0;
        for (JsonNode element : value.values()) {
            Object parsed = scalar(element, field, path + "[" + index + "]");
            index++;
            if (parsed == null) {
                return Optional.empty();
            }
            values.add(parsed);
        }
        return Optional.of(values);
    }

    private @Nullable Object scalar(JsonNode value, DataField field, String path)
    {
        DataFieldType type = field.type();
        Object parsed = switch (type) {
            case TEXT -> value.isString() && value.stringValue().length() <= MAX_TEXT ? value.stringValue() : null;
            case NUMBER -> value.isNumber() ? value.decimalValue() : null;
            case BOOLEAN -> value.isBoolean() ? value.booleanValue() : null;
            case CHOICE -> value.isString() ? value.stringValue() : null;
            case TIME -> value.isString() ? instant(value.stringValue()) : null;
        };
        if (parsed == null) {
            issue(path, "value-invalid", type.name());
            return null;
        }
        if (type == DataFieldType.CHOICE && !field.choices().contains(parsed)) {
            issue(path, "choice-unknown", String.join(", ", field.choices()));
            return null;
        }
        return parsed;
    }

    private static @Nullable Instant instant(String text)
    {
        try {
            return Instant.parse(text);
        }
        catch (DateTimeParseException malformed) {
            return null;
        }
    }

    private void issue(String path, String key, Object... arguments)
    {
        issues.add(FieldIssue.of(path, PREFIX + key, arguments));
    }

    /**
     * What reading a condition gave.
     *
     * @param condition the condition, or {@code null} if it has problems
     * @param issues the problems; empty when the condition is sound
     */
    public record Result(@Nullable Condition condition, List<FieldIssue> issues)
    {
        /** Copies the problems. */
        public Result
        {
            issues = List.copyOf(issues);
        }
    }
}
