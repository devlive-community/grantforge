// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

import static java.util.Objects.requireNonNull;

/** Deletes what the authorization server no longer needs: expired authorizations and retired tokens, old signing keys. */
@Component
public final class OAuthCleanup
{
    private static final Logger LOG = LoggerFactory.getLogger(OAuthCleanup.class);

    private final StoredAuthorizations authorizations;
    private final SigningKeys keys;
    private final Clock clock;

    /**
     * Creates the task.
     *
     * @param authorizations the stored authorizations
     * @param keys the signing keys
     * @param clock the current time
     */
    public OAuthCleanup(StoredAuthorizations authorizations, SigningKeys keys, Clock clock)
    {
        this.authorizations = requireNonNull(authorizations, "authorizations");
        this.keys = requireNonNull(keys, "keys");
        this.clock = requireNonNull(clock, "clock");
    }

    /** Runs the cleanup, hourly by default. */
    @Scheduled(initialDelayString = "${grantforge.oauth.purge-delay:5m}", fixedDelayString = "${grantforge.oauth.purge-interval:1h}")
    public void purge()
    {
        int expired = authorizations.purgeExpired(clock.instant());
        int retired = keys.purgeRetired();
        if (expired + retired > 0) {
            LOG.info("Deleted {} expired OAuth authorizations and {} retired signing keys", expired, retired);
        }
    }
}
