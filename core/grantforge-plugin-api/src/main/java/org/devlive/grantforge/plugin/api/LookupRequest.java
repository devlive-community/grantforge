// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Asks a plugin for existing values of one resource level, to complete what a user types in the policy editor.
 *
 * @param config the service's configuration
 * @param resource the level whose values are wanted
 * @param userInput what the user has typed so far; may be empty
 * @param context values already chosen for other levels, such as the database when tables are wanted
 * @param limit the most values wanted; at least 1
 */
public record LookupRequest(ServiceConfig config, String resource, String userInput, Map<String, List<String>> context,
        int limit)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if the limit is below 1
     */
    public LookupRequest
    {
        requireNonNull(config, "config");
        requireNonNull(resource, "resource");
        requireNonNull(userInput, "userInput");
        context = Map.copyOf(requireNonNull(context, "context").entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))));
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
    }
}
