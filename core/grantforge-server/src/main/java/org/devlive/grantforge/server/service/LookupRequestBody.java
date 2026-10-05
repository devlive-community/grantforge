// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What to look up in a service.
 *
 * @param resource the resource level whose values are wanted
 * @param userInput what the user typed so far; at most 1024 characters, like a policy resource value
 * @param context values chosen for other levels; none when left out
 * @param limit the most values wanted; 20 unless given, at most 100
 */
public record LookupRequestBody(
        @NotBlank @Size(max = 64) @Nullable String resource,
        @Size(max = 1024) @Nullable String userInput,
        @Size(max = 20) Map<String, List<String>> context,
        @Min(1) @Max(100) @Nullable Integer limit)
{
    /** Copies the context; JSON without it, or with levels or values set to {@code null}, leaves them out. */
    @SuppressWarnings("ConstantValue")
    public LookupRequestBody
    {
        context = context == null ? Map.of() : Map.copyOf(context.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream().filter(Objects::nonNull).toList())));
    }
}
