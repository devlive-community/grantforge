// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Turns the rules of a reader into a JPA {@link Specification} of the rows they may use: rows of any allowing rule and of
 * no denying rule; none without an allowing rule. Departments are matched through the membership table and, for
 * sub-departments, through the departments' materialised paths, in subqueries every supported database runs; values are
 * bound as parameters, never written into SQL.
 */
public final class DataScopeSpecifications
{
    private static final char ESCAPE = '\\';

    private DataScopeSpecifications()
    {
    }

    /**
     * Builds the scope of a reader on an entity.
     *
     * @param entity the entity
     * @param rules the reader's rules on the entity and action
     * @param subject the reader
     * @param now the current moment, for the variable {@code now}
     * @param <T> the entity class
     * @return the specification
     */
    public static <T> Specification<T> of(SecuredEntityDefinition entity, DataAccess.Rules rules, DataSubject subject, Instant now)
    {
        requireNonNull(entity, "entity");
        requireNonNull(rules, "rules");
        requireNonNull(subject, "subject");
        return (root, query, builder) -> {
            Context context = new Context(entity, subject, now, root, requireNonNull(query, "query"), builder);
            if (rules.allow().isEmpty()) {
                return builder.disjunction();
            }
            Predicate allowed = builder.or(rules.allow().stream().map(context::rule).toArray(Predicate[]::new));
            if (rules.deny().isEmpty()) {
                return allowed;
            }
            Predicate denied = builder.or(rules.deny().stream().map(context::rule).toArray(Predicate[]::new));
            return builder.and(allowed, builder.not(denied));
        };
    }

    /** Everything one predicate is built from. */
    private record Context(SecuredEntityDefinition entity, DataSubject subject, Instant now, Root<?> root, CommonAbstractCriteria query,
            CriteriaBuilder builder)
    {
        Predicate rule(DataRule rule)
        {
            Predicate scoped = scope(rule);
            String tenant = entity.tenant();
            // Tenant-scoped entities are kept to the tenant by Hibernate; others carry their tenant in a column.
            if (tenant != null && rule.scope() != DataScope.ALL) {
                return builder.and(builder.equal(root.get(tenant), subject.tenantId()), scoped);
            }
            return scoped;
        }

        private Predicate scope(DataRule rule)
        {
            return switch (rule.scope()) {
                case ALL, TENANT -> builder.conjunction();
                case SELF -> entity.owner() == null ? builder.disjunction()
                        : builder.equal(root.get(requireNonNull(entity.owner())), subject.accountId());
                case ORG -> units(subject.orgUnitIds());
                case CUSTOM_ORGS -> units(rule.orgUnitIds());
                case ORG_AND_CHILDREN -> subtree();
                case CONDITION -> rule.condition() == null ? builder.disjunction() : condition(requireNonNull(rule.condition()));
            };
        }

        /** Rows of some departments, directly or through their owner's membership. */
        private Predicate units(Collection<Long> unitIds)
        {
            if (unitIds.isEmpty()) {
                return builder.disjunction();
            }
            if (entity.unit() != null) {
                return root.get(requireNonNull(entity.unit())).in(unitIds);
            }
            if (entity.unitFromOwner() && entity.owner() != null) {
                Subquery<Long> members = query.subquery(Long.class);
                Root<OrgMember> member = members.from(OrgMember.class);
                members.select(member.get("accountId")).where(member.get("orgUnitId").in(unitIds));
                return root.get(requireNonNull(entity.owner())).in(members);
            }
            return builder.disjunction();
        }

        /** Rows of the reader's departments and every department below them. */
        private Predicate subtree()
        {
            if (subject.orgUnitPaths().isEmpty()) {
                return builder.disjunction();
            }
            Subquery<Long> below = query.subquery(Long.class);
            Root<OrgUnit> unit = below.from(OrgUnit.class);
            below.select(unit.get("id")).where(builder.or(subject.orgUnitPaths().stream()
                    .map(path -> builder.like(unit.get("path"), escape(path) + "%", ESCAPE)).toArray(Predicate[]::new)));
            if (entity.unit() != null) {
                return root.get(requireNonNull(entity.unit())).in(below);
            }
            if (entity.unitFromOwner() && entity.owner() != null) {
                Subquery<Long> members = query.subquery(Long.class);
                Root<OrgMember> member = members.from(OrgMember.class);
                members.select(member.get("accountId")).where(member.get("orgUnitId").in(below));
                return root.get(requireNonNull(entity.owner())).in(members);
            }
            return builder.disjunction();
        }

        private Predicate condition(Condition condition)
        {
            if (condition instanceof Condition.AllOf all) {
                return builder.and(all.conditions().stream().map(this::condition).toArray(Predicate[]::new));
            }
            if (condition instanceof Condition.AnyOf any) {
                return builder.or(any.conditions().stream().map(this::condition).toArray(Predicate[]::new));
            }
            if (condition instanceof Condition.Negation negation) {
                return builder.not(this.condition(negation.condition()));
            }
            return comparison((Condition.Comparison) condition);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Predicate comparison(Condition.Comparison comparison)
        {
            Path<Object> field = root.get(comparison.field());
            Class<?> type = field.getJavaType();
            Object operand = operand(comparison, type);
            Expression<Comparable> comparable = (Expression) field;
            return switch (comparison.operator()) {
                case IS_NULL -> builder.isNull(field);
                case NOT_NULL -> builder.isNotNull(field);
                case EQ -> builder.equal(field, requireNonNull(operand));
                // Rows without a value are unequal to any value, as people mean it, though not as SQL compares.
                case NE -> builder.or(builder.isNull(field), builder.notEqual(field, requireNonNull(operand)));
                case LT -> builder.lessThan(comparable, (Comparable) requireNonNull(operand));
                case LTE -> builder.lessThanOrEqualTo(comparable, (Comparable) requireNonNull(operand));
                case GT -> builder.greaterThan(comparable, (Comparable) requireNonNull(operand));
                case GTE -> builder.greaterThanOrEqualTo(comparable, (Comparable) requireNonNull(operand));
                case IN -> in(field, (List<?>) requireNonNull(operand));
                case NOT_IN -> builder.or(builder.isNull(field), builder.not(in(field, (List<?>) requireNonNull(operand))));
                case CONTAINS -> builder.like((Expression) field, "%" + escape((String) requireNonNull(operand)) + "%", ESCAPE);
                case STARTS_WITH -> builder.like((Expression) field, escape((String) requireNonNull(operand)) + "%", ESCAPE);
            };
        }

        private Predicate in(Path<Object> field, List<?> values)
        {
            return values.isEmpty() ? builder.disjunction() : field.in(values);
        }

        /** The comparison's value, as the field's Java type; lists for in and not in. */
        private @Nullable Object operand(Condition.Comparison comparison, Class<?> type)
        {
            if (!comparison.operator().takesValue()) {
                return null;
            }
            ConditionVariable variable = comparison.variable();
            Object value = variable != null ? variable(variable) : comparison.value();
            if (value instanceof List<?> values) {
                return values.stream().map(element -> convert(requireNonNull(element), type)).toList();
            }
            return convert(requireNonNull(value), type);
        }

        private Object variable(ConditionVariable variable)
        {
            return switch (variable) {
                case SUBJECT_ID -> BigDecimal.valueOf(subject.accountId());
                case SUBJECT_USERNAME -> subject.username();
                case SUBJECT_ORG_UNIT_IDS -> subject.orgUnitIds().stream().map(BigDecimal::valueOf).toList();
                case SUBJECT_GROUP_CODES -> subject.groupCodes();
                case SUBJECT_POSITION_CODES -> subject.positionCodes();
                case NOW -> now;
            };
        }
    }

    /** Converts a checked condition value to the Java type of the field it is compared with. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convert(Object value, Class<?> type)
    {
        if (type.isEnum() && value instanceof String name) {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        }
        if (value instanceof BigDecimal number) {
            return number(number, type);
        }
        if (value instanceof Instant moment) {
            if (type == LocalDateTime.class) {
                return LocalDateTime.ofInstant(moment, ZoneOffset.UTC);
            }
            if (type == LocalDate.class) {
                return LocalDate.ofInstant(moment, ZoneOffset.UTC);
            }
            if (type == OffsetDateTime.class) {
                return moment.atOffset(ZoneOffset.UTC);
            }
        }
        return value;
    }

    private static Object number(BigDecimal number, Class<?> type)
    {
        if (type == Long.class || type == long.class) {
            return number.longValue();
        }
        if (type == Integer.class || type == int.class) {
            return number.intValue();
        }
        if (type == Short.class || type == short.class) {
            return number.shortValue();
        }
        if (type == Double.class || type == double.class) {
            return number.doubleValue();
        }
        if (type == Float.class || type == float.class) {
            return number.floatValue();
        }
        return type == BigInteger.class ? number.toBigInteger() : number;
    }

    /** Escapes the wildcards of LIKE in a literal. */
    static String escape(String literal)
    {
        StringBuilder escaped = new StringBuilder(literal.length());
        for (char character : literal.toCharArray()) {
            if (character == ESCAPE || character == '%' || character == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
