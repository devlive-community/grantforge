// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.AuthorizationVersion;
import org.devlive.grantforge.authz.domain.AuthorizationVersionRepository;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.devlive.grantforge.persistence.authz.AuthorizationVersionSink;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The counters of {@code gf_authz_version}: one for the shared catalog and one per tenant, raised by every change
 * that permissions are worked out from, in the changing transaction. Cached permissions remember the counters they
 * were worked out at and are used only while both are unchanged, so every node notices a change made by any node
 * at the next request.
 */
@Component
public final class AuthorizationVersions
        implements AuthorizationVersionSink
{
    private final AuthorizationVersionRepository counters;
    private final Clock clock;

    /**
     * Creates the counters.
     *
     * @param counters the stored counters, changed in the caller's transaction
     * @param clock stamps the changes
     */
    public AuthorizationVersions(AuthorizationVersionRepository counters, Clock clock)
    {
        this.counters = requireNonNull(counters, "counters");
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public void changed(Collection<String> scopes)
    {
        Instant now = clock.instant();
        for (String scope : scopes) {
            // The catalog's counter exists from the start and a tenant's from its first change, its provisioning.
            if (counters.raise(scope, now) == 0) {
                counters.save(AuthorizationVersion.first(scope, now));
            }
        }
    }

    /**
     * Returns the counters of the catalog and of a tenant; a scope never changed counts as 0.
     *
     * @param tenantId the tenant
     * @return the two counters
     */
    public Versions current(long tenantId)
    {
        String tenant = AuthorizationChanges.tenantScope(tenantId);
        Map<String, Long> found = counters.findAllById(List.of(AuthorizationChanges.CATALOG, tenant)).stream()
                .collect(Collectors.toMap(AuthorizationVersion::getScope, AuthorizationVersion::getVersion));
        return new Versions(found.getOrDefault(AuthorizationChanges.CATALOG, 0L), found.getOrDefault(tenant, 0L));
    }

    /**
     * The counters cached permissions depend on.
     *
     * @param catalog the counter of the shared catalog
     * @param tenant the counter of the tenant
     */
    public record Versions(long catalog, long tenant)
    {
    }
}
