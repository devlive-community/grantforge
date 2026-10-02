// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.service.ServiceCommand;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A service to add or change; the type is only given when adding.
 *
 * @param serviceType the service type's name; ignored when changing a service
 * @param name the service's name
 * @param label what the console shows
 * @param description a longer explanation
 * @param enabled whether the service is in use; in use unless said otherwise
 * @param values the settings by name, none when left out; a blank secret keeps the stored one
 */
public record ServiceRequest(
        @Size(max = 64) @Nullable String serviceType,
        @NotBlank @Size(max = 64) @Nullable String name,
        @NotBlank @Size(max = 128) @Nullable String label,
        @Size(max = 512) @Nullable String description,
        @Nullable Boolean enabled,
        @Size(max = 100) Map<String, @Size(max = 65536) String> values)
{
    /** Copies the settings; JSON without them, or with settings set to {@code null}, leaves them out. */
    @SuppressWarnings("ConstantValue")
    public ServiceRequest
    {
        values = Map.copyOf(settings(values));
    }

    /**
     * Turns the request into a command.
     *
     * @return the command
     */
    public ServiceCommand command()
    {
        return new ServiceCommand(String.valueOf(name).strip(), String.valueOf(label), description, enabled == null || enabled,
                values);
    }

    /**
     * Copies settings from JSON, leaving out those without a value.
     *
     * @param values the settings, or {@code null}
     * @return the settings
     */
    @SuppressWarnings("ConstantValue")
    static Map<String, String> settings(@Nullable Map<String, String> values)
    {
        return values == null ? Map.of() : values.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
