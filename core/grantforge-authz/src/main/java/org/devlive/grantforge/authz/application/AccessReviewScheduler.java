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

/** Completes due rounds of access reviews and starts scheduled ones every few minutes. */
@Component
public final class AccessReviewScheduler
{
    private static final Logger LOG = LoggerFactory.getLogger(AccessReviewScheduler.class);

    private final AccessReviewService reviews;

    /**
     * Creates the scheduler.
     *
     * @param reviews runs the rounds
     */
    public AccessReviewScheduler(AccessReviewService reviews)
    {
        this.reviews = requireNonNull(reviews, "reviews");
    }

    /** Completes the rounds that are due and starts those whose time has come. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void run()
    {
        int ran = reviews.runDue();
        if (ran > 0) {
            LOG.info("Completed or started {} access review rounds", ran);
        }
    }
}
