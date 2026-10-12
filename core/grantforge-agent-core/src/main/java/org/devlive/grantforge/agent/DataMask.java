// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * How a masking policy says a value is shown to a user: the mask type, its transformer from the service type and the
 * value a policy item gives for types such as {@code custom}. Immutable.
 */
public final class DataMask
{
    /** Where a transformer puts the masked column. */
    public static final String COLUMN = "{col}";

    /** Where a transformer puts the policy item's value. */
    public static final String VALUE = "{expr}";

    private final long policyVersion;
    private final long policyId;
    private final String maskType;
    private final @Nullable String transformer;
    private final @Nullable String value;

    DataMask(long policyVersion, long policyId, String maskType, @Nullable String transformer, @Nullable String value)
    {
        this.policyVersion = policyVersion;
        this.policyId = policyId;
        this.maskType = requireNonNull(maskType, "maskType");
        this.transformer = transformer;
        this.value = value;
    }

    /**
     * Returns the expression that shows a column masked: the transformer with the policy item's value in place of
     * {@value #VALUE} and then the column in place of {@value #COLUMN}, so a value may name the column too.
     *
     * @param column the column as the target system writes it
     * @return the expression, or {@code null} when the service type gives the mask type no transformer and the agent
     *         applies it by name
     * @throws IllegalStateException when the transformer takes a value the policy item does not give
     */
    public @Nullable String expression(String column)
    {
        requireNonNull(column, "column");
        if (transformer == null) {
            return null;
        }
        String filled = transformer;
        if (filled.contains(VALUE)) {
            if (value == null || value.isEmpty()) {
                throw new IllegalStateException("mask type " + maskType + " of policy " + policyId + " needs a value");
            }
            filled = filled.replace(VALUE, value);
        }
        return filled.replace(COLUMN, column);
    }

    /**
     * Returns the policy version of the snapshot that decided.
     *
     * @return the version
     */
    public long policyVersion()
    {
        return policyVersion;
    }

    /**
     * Returns the masking policy that applies.
     *
     * @return its id
     */
    public long policyId()
    {
        return policyId;
    }

    /**
     * Returns the mask type.
     *
     * @return its name
     */
    public String maskType()
    {
        return maskType;
    }

    /**
     * Returns the transformer the service type gives the mask type.
     *
     * @return it, or {@code null} if the agent applies the type by name
     */
    public @Nullable String transformer()
    {
        return transformer;
    }

    /**
     * Returns the value the policy item gives.
     *
     * @return it, or {@code null}
     */
    public @Nullable String value()
    {
        return value;
    }

    @Override
    public String toString()
    {
        return maskType + " by policy " + policyId;
    }
}
