// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Purges access events past the retention period now and then ({@code grantforge.access-audit.purge-interval}, hourly
 * unless configured). Several nodes may purge at once; they only remove the same rows.
 */
@Component
public final class AccessAuditRetention
{
    private final AccessAudit audit;

    /**
     * Creates the job.
     *
     * @param audit purges the events
     */
    public AccessAuditRetention(AccessAudit audit)
    {
        this.audit = requireNonNull(audit, "audit");
    }

    /** Purges old events. */
    @Scheduled(initialDelayString = "${grantforge.access-audit.purge-delay:5m}",
            fixedDelayString = "${grantforge.access-audit.purge-interval:1h}")
    public void purge()
    {
        audit.purge();
    }
}
