// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataFieldType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** How a condition compares a field, and the kinds of field each comparison applies to. */
public enum ComparisonOperator
{
    /** Equal to the value. */
    EQ("eq", EnumSet.allOf(DataFieldType.class)),
    /** Not equal to the value. */
    NE("ne", EnumSet.allOf(DataFieldType.class)),
    /** Less than the value. */
    LT("lt", EnumSet.of(DataFieldType.NUMBER, DataFieldType.TIME)),
    /** Less than or equal to the value. */
    LTE("lte", EnumSet.of(DataFieldType.NUMBER, DataFieldType.TIME)),
    /** Greater than the value. */
    GT("gt", EnumSet.of(DataFieldType.NUMBER, DataFieldType.TIME)),
    /** Greater than or equal to the value. */
    GTE("gte", EnumSet.of(DataFieldType.NUMBER, DataFieldType.TIME)),
    /** One of the values. */
    IN("in", EnumSet.of(DataFieldType.TEXT, DataFieldType.NUMBER, DataFieldType.CHOICE)),
    /** None of the values. */
    NOT_IN("not_in", EnumSet.of(DataFieldType.TEXT, DataFieldType.NUMBER, DataFieldType.CHOICE)),
    /** Text containing the value. */
    CONTAINS("contains", EnumSet.of(DataFieldType.TEXT)),
    /** Text starting with the value. */
    STARTS_WITH("starts_with", EnumSet.of(DataFieldType.TEXT)),
    /** Without a value; takes no value. */
    IS_NULL("is_null", EnumSet.allOf(DataFieldType.class)),
    /** With a value; takes no value. */
    NOT_NULL("not_null", EnumSet.allOf(DataFieldType.class));

    private final String symbol;
    private final Set<DataFieldType> types;

    ComparisonOperator(String symbol, Set<DataFieldType> types)
    {
        this.symbol = symbol;
        this.types = types;
    }

    /**
     * Finds an operator by the name conditions use.
     *
     * @param symbol such as {@code eq} or {@code starts_with}
     * @return the operator, or empty
     */
    public static Optional<ComparisonOperator> of(String symbol)
    {
        return Arrays.stream(values()).filter(operator -> operator.symbol.equals(symbol)).findFirst();
    }

    /**
     * Returns the name conditions use.
     *
     * @return such as {@code eq}
     */
    public String symbol()
    {
        return symbol;
    }

    /**
     * Returns whether the operator compares fields of a kind.
     *
     * @param type the kind of field
     * @return {@code true} if it does
     */
    public boolean appliesTo(DataFieldType type)
    {
        return types.contains(type);
    }

    /**
     * Returns whether the operator takes a list of values.
     *
     * @return {@code true} for {@link #IN} and {@link #NOT_IN}
     */
    public boolean takesList()
    {
        return this == IN || this == NOT_IN;
    }

    /**
     * Returns whether the operator takes a value at all.
     *
     * @return {@code false} for {@link #IS_NULL} and {@link #NOT_NULL}
     */
    public boolean takesValue()
    {
        return this != IS_NULL && this != NOT_NULL;
    }
}
