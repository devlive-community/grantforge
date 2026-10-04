// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Starts the sample shop. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopApplication
{
    /**
     * Starts the shop.
     *
     * @param args Spring Boot arguments
     */
    public static void main(String[] args)
    {
        SpringApplication.run(ShopApplication.class, args);
    }
}
