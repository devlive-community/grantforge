// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication
@ComponentScan(value = {
        "org.devlive.grantforge.security",
        "org.devlive.grantforge.service",
        "org.devlive.grantforge.server"
})
public class GrantForge {

    public static void main(String[] args) {
        SpringApplication.run(org.devlive.grantforge.server.GrantForge.class, args);
    }
}
