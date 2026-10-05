// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/** Takes back the roles of ended grants every few minutes. */
@Component
public final class AccessRequestScheduler
{
    private static final Logger LOG = LoggerFactory.getLogger(AccessRequestScheduler.class);

    private final AccessRequestService requests;

    /**
     * Creates the scheduler.
     *
     * @param requests takes the grants back
     */
    public AccessRequestScheduler(AccessRequestService requests)
    {
        this.requests = requireNonNull(requests, "requests");
    }

    /** Takes back the roles of grants whose period is over. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void expire()
    {
        int ended = requests.expire();
        if (ended > 0) {
            LOG.info("Took back {} ended access grants", ended);
        }
    }
}
