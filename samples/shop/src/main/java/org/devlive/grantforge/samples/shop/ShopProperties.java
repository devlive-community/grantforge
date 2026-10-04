// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * What the shop's browser front end needs to know.
 *
 * @param issuer GrantForge's address, where users sign in
 * @param browserClientId the public client the browser signs users in with
 * @param sdkDirectory the built @grantforge/client (its dist directory), served at /sdk/
 */
@ConfigurationProperties("shop")
public record ShopProperties(String issuer, String browserClientId, Path sdkDirectory)
{
    /** Checks the values. */
    public ShopProperties
    {
        requireNonNull(issuer, "shop.issuer");
        requireNonNull(browserClientId, "shop.browser-client-id");
        requireNonNull(sdkDirectory, "shop.sdk-directory");
    }
}
