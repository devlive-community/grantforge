// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.devlive.grantforge.service.domain.Enforcer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNullElse;

/**
 * An access an agent checked.
 *
 * @param eventId the agent's name for the event, unique within the service, such as a UUID; sending it again stores nothing
 * @param occurredAt when
 * @param user who
 * @param clientIp from where
 * @param resource the resource, its levels joined as the system shows them, such as {@code sales.orders.ssn}
 * @param resourceType the lowest level of the resource
 * @param accessType the access type checked
 * @param action the operation of the system, such as {@code open} or {@code SELECT}
 * @param outcome allowed or denied
 * @param policyId the policy that decided, as the snapshot names it, or left out if none did
 * @param policyVersion the policy version the agent applied
 * @param enforcer who decided; GrantForge when a policy did, the system otherwise, unless said
 * @param request the request, such as an SQL statement; cut to 1000 characters
 */
public record AccessEventRequest(
        @NotBlank @Size(max = 64) @Nullable String eventId,
        @NotNull @Nullable Instant occurredAt,
        @NotBlank @Size(max = 128) @Nullable String user,
        @Size(max = 45) @Nullable String clientIp,
        @NotBlank @Size(max = AccessEvent.MAX_TEXT) @Nullable String resource,
        @Size(max = 64) @Nullable String resourceType,
        @NotBlank @Size(max = 64) @Nullable String accessType,
        @Size(max = 128) @Nullable String action,
        @NotNull @Nullable AccessOutcome outcome,
        @Pattern(regexp = "\\d{1,19}") @Nullable String policyId,
        @Nullable Long policyVersion,
        @Nullable Enforcer enforcer,
        @Nullable String request)
{
    /**
     * Turns the request into the fields of an event.
     *
     * @return the fields
     */
    public AccessEvent.Fields fields()
    {
        Enforcer decidedBy = enforcer != null ? enforcer : policyId != null ? Enforcer.GRANTFORGE : Enforcer.NATIVE;
        String text = request == null || request.isBlank() ? null
                : request.length() > AccessEvent.MAX_TEXT ? request.substring(0, AccessEvent.MAX_TEXT) : request;
        return new AccessEvent.Fields(String.valueOf(eventId), requireNonNullElse(occurredAt, Instant.EPOCH), String.valueOf(user),
                blankToNull(clientIp), String.valueOf(resource), blankToNull(resourceType), String.valueOf(accessType), blankToNull(action),
                requireNonNullElse(outcome, AccessOutcome.DENIED), policyId == null ? null : Long.valueOf(policyId), policyVersion, decidedBy,
                text);
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
