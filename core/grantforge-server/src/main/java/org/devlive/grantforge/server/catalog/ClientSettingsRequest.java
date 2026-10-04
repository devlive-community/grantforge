// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.ClientSettings;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNullElse;

/**
 * What an OAuth client may do; the service checks the rules that combine fields.
 *
 * @param name its name
 * @param redirectUris where users may be sent back to
 * @param scopes what it may ask for
 * @param grants how it may obtain tokens
 * @param accessTokenMinutes how long access tokens last; 15 when absent
 * @param refreshTokenHours how long refresh tokens last; 30 days when absent
 * @param enabled whether it may obtain tokens; {@code true} when absent
 */
public record ClientSettingsRequest(
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 10) @Nullable List<@NotBlank String> redirectUris,
        @Size(max = 10) @Nullable Set<@NotBlank String> scopes,
        @NotNull @Size(min = 1, max = 3) @Nullable Set<@NotNull ClientGrant> grants,
        @Nullable Long accessTokenMinutes,
        @Nullable Long refreshTokenHours,
        @Nullable Boolean enabled)
{
    /** Copies the collections; left out, they take their defaults or validation refuses them. */
    // Absent stays absent: validation refuses it or the default applies later.
    @SuppressWarnings("PMD.NullAssignment")
    public ClientSettingsRequest
    {
        redirectUris = redirectUris == null ? null : List.copyOf(redirectUris);
        scopes = scopes == null ? null : Set.copyOf(scopes);
        grants = grants == null ? null : Set.copyOf(grants);
    }

    /** Default lifetime of access tokens. */
    static final long ACCESS_MINUTES = 15;

    /** Default lifetime of refresh tokens. */
    static final long REFRESH_HOURS = 30 * 24;

    /**
     * Converts the request, filling in defaults.
     *
     * @return the settings
     */
    ClientSettings settings()
    {
        return new ClientSettings(requireNonNullElse(name, ""), requireNonNullElse(redirectUris, List.of()),
                requireNonNullElse(scopes, Set.of()), requireNonNullElse(grants, Set.of()),
                Duration.ofMinutes(requireNonNullElse(accessTokenMinutes, ACCESS_MINUTES)),
                Duration.ofHours(requireNonNullElse(refreshTokenHours, REFRESH_HOURS)), requireNonNullElse(enabled, true));
    }
}
