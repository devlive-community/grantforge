// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A checked condition on the fields of a secured entity: comparisons combined with and, or and not. Values are either
 * literals, already of the field's kind ({@code String}, {@code java.math.BigDecimal}, {@code Boolean},
 * {@code java.time.Instant}, or a list of them), or {@link ConditionVariable variables}.
 */
public sealed interface Condition
        permits Condition.AllOf, Condition.AnyOf, Condition.Negation, Condition.Comparison
{
    /**
     * Every condition holds.
     *
     * @param conditions at least one
     */
    record AllOf(List<Condition> conditions)
            implements Condition
    {
        /** Copies the conditions. */
        public AllOf
        {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * At least one condition holds.
     *
     * @param conditions at least one
     */
    record AnyOf(List<Condition> conditions)
            implements Condition
    {
        /** Copies the conditions. */
        public AnyOf
        {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * The condition does not hold.
     *
     * @param condition the condition
     */
    record Negation(Condition condition)
            implements Condition
    {
        /** Checks the condition. */
        public Negation
        {
            requireNonNull(condition, "condition");
        }
    }

    /**
     * A field compared with a value.
     *
     * @param field the attribute, a filterable field of the entity
     * @param operator how it is compared
     * @param value the literal value, or {@code null} for a variable or no value
     * @param variable the variable, or {@code null}
     */
    record Comparison(String field, ComparisonOperator operator, @Nullable Object value, @Nullable ConditionVariable variable)
            implements Condition
    {
        /**
         * Checks the parts.
         *
         * @throws IllegalArgumentException if both a value and a variable are given
         */
        public Comparison
        {
            requireNonNull(field, "field");
            requireNonNull(operator, "operator");
            if (value != null && variable != null) {
                throw new IllegalArgumentException("a comparison takes a value or a variable, not both");
            }
            if (value instanceof List<?> values) {
                value = List.copyOf(values);
            }
        }
    }
}
