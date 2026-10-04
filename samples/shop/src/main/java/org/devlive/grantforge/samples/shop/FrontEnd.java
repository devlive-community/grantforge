// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.shop;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/** Serves what the browser front end needs besides its own files: the SDK and where to sign in. */
@Configuration(proxyBeanMethods = false)
@RestController
public class FrontEnd
        implements WebMvcConfigurer
{
    private final ShopProperties properties;

    /**
     * Creates the front end's back end.
     *
     * @param properties where GrantForge and the SDK are
     */
    public FrontEnd(ShopProperties properties)
    {
        this.properties = requireNonNull(properties, "properties");
    }

    /**
     * Tells the front end where users sign in and as which client.
     *
     * @return the issuer and client ID
     */
    @GetMapping("/config.json")
    public Map<String, String> config()
    {
        return Map.of("issuer", properties.issuer(), "clientId", properties.browserClientId());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        registry.addResourceHandler("/sdk/**").addResourceLocations(properties.sdkDirectory().toUri().toString());
    }
}
