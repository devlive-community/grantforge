// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

import static java.util.Objects.requireNonNull;

/** Syncs directories whose interval passed, checked once a minute. */
@Component
public class IdentitySourceScheduler
{
    private static final Logger LOG = LoggerFactory.getLogger(IdentitySourceScheduler.class);

    private final IdentitySourceService service;
    private final Clock clock;

    /**
     * Creates the scheduler.
     *
     * @param service syncs the sources
     * @param clock the current time
     */
    public IdentitySourceScheduler(IdentitySourceService service, Clock clock)
    {
        this.service = requireNonNull(service, "service");
        this.clock = requireNonNull(clock, "clock");
    }

    /** Syncs the due directories one after the other; one failing does not stop the rest. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void syncDue()
    {
        for (IdentitySource source : service.dueForSync(clock.instant())) {
            long tenantId = requireNonNull(source.getTenantId(), "tenantId");
            try {
                SyncReport report = TenantContext.callInTenant(tenantId, () -> service.sync(null, source.requireId()));
                LOG.info("Synced identity source '{}': {}", source.getCode(), report.summary());
            }
            catch (GrantForgeException failed) {
                LOG.warn("Could not sync identity source '{}': {}", source.getCode(), failed.getMessage());
            }
        }
    }
}
