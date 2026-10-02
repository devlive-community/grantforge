// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataScope;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One scope a reader's roles allow or deny on an entity: a data policy, or what a system role implies.
 *
 * @param scope which rows
 * @param condition the condition of {@link DataScope#CONDITION}, or {@code null}
 * @param orgUnitIds the departments of {@link DataScope#CUSTOM_ORGS}
 */
public record DataRule(DataScope scope, @Nullable Condition condition, List<Long> orgUnitIds)
{
    /** Checks and copies the values. */
    public DataRule
    {
        requireNonNull(scope, "scope");
        orgUnitIds = List.copyOf(orgUnitIds);
    }

    /**
     * A rule of a scope without condition or departments.
     *
     * @param scope the scope
     * @return the rule
     */
    public static DataRule of(DataScope scope)
    {
        return new DataRule(scope, null, List.of());
    }
}
