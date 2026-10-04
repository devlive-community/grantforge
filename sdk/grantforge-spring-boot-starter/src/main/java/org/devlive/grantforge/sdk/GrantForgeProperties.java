// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Where GrantForge is and how long answers are kept.
 *
 * @param baseUrl GrantForge's address, such as {@code https://grantforge.example.com}; the starter stays off without it
 * @param cacheTtl how long an answer is used before it is revalidated (cheaply, with its ETag); zero asks every time
 * @param cacheSize how many users' answers are kept
 * @param timeout how long a call to GrantForge may take
 */
@ConfigurationProperties("grantforge.client")
public record GrantForgeProperties(@Nullable URI baseUrl, @DefaultValue("30s") Duration cacheTtl, @DefaultValue("10000") int cacheSize,
        @DefaultValue("5s") Duration timeout)
{
    /** Checks the values. */
    public GrantForgeProperties
    {
        requireNonNull(cacheTtl, "cacheTtl");
        requireNonNull(timeout, "timeout");
        if (cacheTtl.isNegative() || cacheSize < 1 || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("grantforge.client: cache-ttl must not be negative, cache-size must be positive and"
                    + " timeout must be positive");
        }
    }
}
