// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Console session settings ({@code grantforge.security.sessions.*}); the idle timeout itself is Spring Session's
 * {@code spring.session.timeout}.
 *
 * @param maxPerAccount how many sessions one account may hold at once, 0 (no limit) to 100; signing in beyond
 *         the limit ends the account's least recently used sessions
 * @param activityInterval how often a session's last activity is written, between 10 seconds and 1 hour; the
 *         session list shows activity at this precision
 */
@ConfigurationProperties("grantforge.security.sessions")
public record SessionProperties(@DefaultValue("0") int maxPerAccount, @DefaultValue("1m") Duration activityInterval)
{
    /**
     * Validates the settings.
     *
     * @param maxPerAccount the session limit per account
     * @param activityInterval the activity precision
     * @throws IllegalArgumentException if a value is out of range
     */
    public SessionProperties
    {
        if (maxPerAccount < 0 || maxPerAccount > 100) {
            throw new IllegalArgumentException("grantforge.security.sessions.max-per-account must be 0-100 but was "
                    + maxPerAccount);
        }
        requireNonNull(activityInterval, "activityInterval");
        if (activityInterval.compareTo(Duration.ofSeconds(10)) < 0 || activityInterval.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("grantforge.security.sessions.activity-interval must be 10s-1h but was "
                    + activityInterval);
        }
    }

    /**
     * Returns the defaults: no limit, activity written once a minute.
     *
     * @return the defaults
     */
    public static SessionProperties defaults()
    {
        return new SessionProperties(0, Duration.ofMinutes(1));
    }
}
