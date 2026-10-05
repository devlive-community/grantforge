// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Limits queries of {@link GrantForgeEntity} entities to the rows the current user may use, as GrantForge's data
 * policies say: {@code repository.findAll(scopes.scope(Order.class, DataAction.READ).and(myFilters))}. A row is in if an
 * allowing rule covers it and no denying rule does; rules other than "all rows" also require the user's tenant when the
 * entity maps a tenant column.
 */
public final class GrantForgeDataScopes
{
    private static final char ESCAPE = '\\';

    private final GrantForgeClient client;
    private final AccessTokenResolver tokens;
    private final Clock clock;

    /**
     * Creates the scopes.
     *
     * @param client asks GrantForge
     * @param tokens finds the current user's token
     * @param clock the current time, for conditions on {@code now}
     */
    public GrantForgeDataScopes(GrantForgeClient client, AccessTokenResolver tokens, Clock clock)
    {
        this.client = requireNonNull(client, "client");
        this.tokens = requireNonNull(tokens, "tokens");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns the rows of an entity the current user may use for an action.
     *
     * @param type the entity class, annotated with {@link GrantForgeEntity}
     * @param action the action
     * @param <T> the entity class
     * @return the scope, to combine with the query's own filters
     * @throws GrantForgeException as {@link GrantForge#current()}
     * @throws IllegalArgumentException if the class is not annotated
     */
    public <T> Specification<T> scope(Class<T> type, DataAction action)
    {
        String token = tokens.currentToken();
        if (token == null) {
            throw new GrantForgeException(Reason.UNAUTHENTICATED, "the request carries no GrantForge access token", null);
        }
        return of(type, client.dataAccess(token), action, clock.instant());
    }

    /**
     * Returns the rows of an entity some rules allow for an action.
     *
     * @param type the entity class, annotated with {@link GrantForgeEntity}
     * @param access the user's rules
     * @param action the action
     * @param now the current time
     * @param <T> the entity class
     * @return the scope
     * @throws IllegalArgumentException if the class is not annotated with {@link GrantForgeEntity}
     */
    public static <T> Specification<T> of(Class<T> type, UserDataAccess access, DataAction action, Instant now)
    {
        GrantForgeEntity entity = type.getAnnotation(GrantForgeEntity.class);
        if (entity == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @GrantForgeEntity");
        }
        UserDataAccess.EntityRules rules = access.rules(entity.code(), action).orElse(null);
        return (root, query, builder) -> {
            if (rules == null || rules.allow().isEmpty()) {
                return builder.disjunction();
            }
            Context context = new Context(entity, access.subject(), now, root, requireNonNull(query, "query"), builder);
            Predicate allowed = builder.or(rules.allow().stream().map(context::rule).toArray(Predicate[]::new));
            if (rules.deny().isEmpty()) {
                return allowed;
            }
            Predicate denied = builder.or(rules.deny().stream().map(context::rule).toArray(Predicate[]::new));
            return builder.and(allowed, builder.not(denied));
        };
    }

    /** One query's view of the rules. */
    private record Context(GrantForgeEntity entity, UserDataAccess.Subject subject, Instant now, Root<?> root, CommonAbstractCriteria query,
            CriteriaBuilder builder)
    {
        Predicate rule(UserDataAccess.Rule rule)
        {
            Predicate scoped = scope(rule);
            if (!entity.tenant().isEmpty() && rule.scope() != DataScope.ALL) {
                return builder.and(equal(entity.tenant(), subject.tenantId()), scoped);
            }
            return scoped;
        }

        private Predicate scope(UserDataAccess.Rule rule)
        {
            return switch (rule.scope()) {
                case ALL, TENANT -> builder.conjunction();
                case SELF -> entity.owner().isEmpty() ? builder.disjunction() : equal(entity.owner(), subject.accountId());
                case ORG -> units(subject.orgUnitIds());
                case ORG_AND_CHILDREN -> units(subject.orgUnitsAndBelow());
                case CUSTOM_ORGS -> units(rule.orgUnitIds());
                case CONDITION -> rule.condition() == null ? builder.disjunction() : condition(requireNonNull(rule.condition()));
            };
        }

        private Predicate equal(String attribute, String id)
        {
            Path<Object> path = root.get(attribute);
            return builder.equal(path, identifier(id, path.getJavaType()));
        }

        private Predicate units(List<String> ids)
        {
            if (entity.unit().isEmpty() || ids.isEmpty()) {
                return builder.disjunction();
            }
            Path<Object> path = root.get(entity.unit());
            return path.in(ids.stream().map(id -> identifier(id, path.getJavaType())).toList());
        }

        private Predicate condition(JsonNode node)
        {
            if (node.has("and") || node.has("or")) {
                List<Predicate> parts = new ArrayList<>();
                for (JsonNode part : node.has("and") ? node.get("and") : node.get("or")) {
                    parts.add(condition(part));
                }
                Predicate[] all = parts.toArray(Predicate[]::new);
                return node.has("and") ? builder.and(all) : builder.or(all);
            }
            if (node.has("not")) {
                return builder.not(condition(node.get("not")));
            }
            return comparison(node.path("field").asString(), node.path("op").asString(), node.get("value"));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Predicate comparison(String field, String operator, @Nullable JsonNode value)
        {
            Path<Object> path = root.get(field);
            Class<?> type = path.getJavaType();
            Expression<Comparable> comparable = (Expression) path;
            if ("is_null".equals(operator)) {
                return builder.isNull(path);
            }
            if ("not_null".equals(operator)) {
                return builder.isNotNull(path);
            }
            if (value == null) {
                // A comparison without a value matches nothing, like an operator this starter does not know.
                return builder.disjunction();
            }
            return switch (operator) {
                case "eq" -> builder.equal(path, single(value, type));
                // Rows without a value are unequal to any value, as people mean it, though not as SQL compares.
                case "ne" -> builder.or(builder.isNull(path), builder.notEqual(path, single(value, type)));
                case "lt" -> builder.lessThan(comparable, (Comparable) single(value, type));
                case "lte" -> builder.lessThanOrEqualTo(comparable, (Comparable) single(value, type));
                case "gt" -> builder.greaterThan(comparable, (Comparable) single(value, type));
                case "gte" -> builder.greaterThanOrEqualTo(comparable, (Comparable) single(value, type));
                case "in" -> in(path, list(value, type));
                case "not_in" -> builder.or(builder.isNull(path), builder.not(in(path, list(value, type))));
                case "contains" -> builder.like((Expression) path, "%" + escape(String.valueOf(single(value, type))) + "%", ESCAPE);
                case "starts_with" -> builder.like((Expression) path, escape(String.valueOf(single(value, type))) + "%", ESCAPE);
                // An operator this starter does not know matches nothing rather than everything.
                default -> builder.disjunction();
            };
        }

        private Predicate in(Path<Object> path, List<Object> values)
        {
            return values.isEmpty() ? builder.disjunction() : path.in(values);
        }

        private Object single(JsonNode value, Class<?> type)
        {
            if (value.isObject()) {
                Object variable = variable(value.path("var").asString());
                if (variable instanceof List<?>) {
                    throw new IllegalArgumentException("variable " + value + " is a list");
                }
                return convert(variable, type);
            }
            return literal(value, type);
        }

        private List<Object> list(JsonNode given, Class<?> type)
        {
            List<Object> values = new ArrayList<>();
            if (given.isObject()) {
                Object variable = variable(given.path("var").asString());
                for (Object element : variable instanceof List<?> elements ? elements : List.of(variable)) {
                    values.add(convert(requireNonNull(element), type));
                }
                return values;
            }
            for (JsonNode element : given) {
                values.add(literal(element, type));
            }
            return values;
        }

        private Object variable(String key)
        {
            return switch (key) {
                case "subject.id" -> subject.accountId();
                case "subject.username" -> subject.username();
                case "subject.orgUnitIds" -> subject.orgUnitIds();
                case "subject.groupCodes" -> subject.groupCodes();
                case "subject.positionCodes" -> subject.positionCodes();
                case "now" -> now;
                default -> throw new IllegalArgumentException("unknown condition variable " + key);
            };
        }
    }

    private static Object literal(JsonNode value, Class<?> type)
    {
        if (value.isNumber()) {
            return number(value.decimalValue(), type);
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return convert(value.asString(), type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(Object value, Class<?> type)
    {
        if (value instanceof Instant moment) {
            return moment(moment, type);
        }
        if (!(value instanceof String text)) {
            return value;
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) type, text);
        }
        if (Number.class.isAssignableFrom(box(type))) {
            return number(new BigDecimal(text), type);
        }
        if (box(type) == Boolean.class) {
            return Boolean.valueOf(text);
        }
        if (type == Instant.class || type == LocalDateTime.class || type == LocalDate.class || type == OffsetDateTime.class
                || type == ZonedDateTime.class || type == Date.class) {
            return moment(Instant.parse(text), type);
        }
        return text;
    }

    private static Object identifier(String id, Class<?> type)
    {
        return convert(id, type);
    }

    private static Object moment(Instant moment, Class<?> type)
    {
        if (type == LocalDateTime.class) {
            return LocalDateTime.ofInstant(moment, ZoneOffset.UTC);
        }
        if (type == LocalDate.class) {
            return LocalDate.ofInstant(moment, ZoneOffset.UTC);
        }
        if (type == OffsetDateTime.class) {
            return moment.atOffset(ZoneOffset.UTC);
        }
        if (type == ZonedDateTime.class) {
            return moment.atZone(ZoneOffset.UTC);
        }
        if (type == Date.class) {
            return Date.from(moment);
        }
        return moment;
    }

    private static Object number(BigDecimal number, Class<?> type)
    {
        Class<?> boxed = box(type);
        if (boxed == Long.class) {
            return number.longValueExact();
        }
        if (boxed == Integer.class) {
            return number.intValueExact();
        }
        if (boxed == Short.class) {
            return number.shortValueExact();
        }
        if (boxed == Double.class) {
            return number.doubleValue();
        }
        if (boxed == Float.class) {
            return number.floatValue();
        }
        if (boxed == BigInteger.class) {
            return number.toBigIntegerExact();
        }
        if (boxed == String.class) {
            return number.toPlainString();
        }
        return number;
    }

    private static Class<?> box(Class<?> type)
    {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "long" -> Long.class;
            case "int" -> Integer.class;
            case "short" -> Short.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "boolean" -> Boolean.class;
            default -> type;
        };
    }

    private static String escape(String text)
    {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
