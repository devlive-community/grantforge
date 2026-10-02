// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A configuration to test, saved or not.
 *
 * @param serviceType the service type's name
 * @param serviceId the service whose stored secrets fill blank secrets, or {@code null} for a new service
 * @param name the service's name, for the plugin's messages
 * @param values the settings by name, none when left out
 */
public record ConnectionTestRequest(
        @NotBlank @Size(max = 64) @Nullable String serviceType,
        @Size(max = 20) @Nullable String serviceId,
        @Size(max = 64) @Nullable String name,
        @Size(max = 100) Map<String, @Size(max = 65536) String> values)
{
    /** Copies the settings; JSON without them, or with settings set to {@code null}, leaves them out. */
    @SuppressWarnings("ConstantValue")
    public ConnectionTestRequest
    {
        values = Map.copyOf(ServiceRequest.settings(values));
    }
}
