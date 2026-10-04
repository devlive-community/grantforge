// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Settings of the authorization server.
 *
 * @param issuer the issuer identifier, such as {@code https://auth.example.com}; when absent, the URL the request reached,
 *        which suits a single host name but not several
 * @param signingKeyRotation how long a signing key signs before a new one takes over; zero turns automatic rotation off
 * @param signingKeyRetention how long a retired key stays published, longer than any token it signed lives
 */
@ConfigurationProperties("grantforge.oauth")
public record OAuthProperties(@Nullable String issuer, @DefaultValue("90d") Duration signingKeyRotation,
        @DefaultValue("2d") Duration signingKeyRetention)
{
    /** Longest lifetime of a token a key signs: access tokens last at most a day. */
    static final Duration LONGEST_TOKEN = Duration.ofDays(1);

    /** Checks the durations. */
    public OAuthProperties
    {
        requireNonNull(signingKeyRotation, "signingKeyRotation");
        requireNonNull(signingKeyRetention, "signingKeyRetention");
        if (signingKeyRotation.isNegative()) {
            throw new IllegalArgumentException("grantforge.oauth.signing-key-rotation must not be negative");
        }
        if (signingKeyRetention.compareTo(LONGEST_TOKEN) <= 0) {
            throw new IllegalArgumentException("grantforge.oauth.signing-key-retention must exceed a day, the longest token lifetime, but was "
                    + signingKeyRetention);
        }
        issuer = Strings.blankToNull(issuer);
    }
}
