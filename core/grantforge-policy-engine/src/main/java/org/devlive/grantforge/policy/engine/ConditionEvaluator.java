// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.List;

/**
 * Decides whether a condition of a policy item holds for a request, such as whether the client address lies in
 * one of the condition's ranges. Supplied to the engine per condition type; must be thread-safe.
 */
@FunctionalInterface
public interface ConditionEvaluator
{
    /**
     * Returns whether the condition holds.
     *
     * @param values the condition's values
     * @param request the request
     * @return {@code true} if it holds
     */
    boolean holds(List<String> values, AccessRequest request);
}
